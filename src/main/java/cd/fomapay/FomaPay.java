package cd.fomapay;

import cd.fomapay.internal.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client serveur FomaPay. Sûr pour un usage concurrent : créez-en un seul et partagez-le.
 *
 * <pre>{@code
 * FomaPay fomapay = new FomaPay("sk_test_…");
 * Payment payment = fomapay.payments().create(
 *     PaymentParams.builder().amount(25).currency(Currency.USD).orderId("CMD-8921").build());
 * }</pre>
 */
public final class FomaPay {
    public static final String VERSION = "0.1.0";
    public static final String DEFAULT_BASE_URL = "https://api.fomapay.cd";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_RETRIES = 2;
    private static final long MAX_BACKOFF_MS = 8_000;

    /** Attente entre deux tentatives ; remplaçable dans les tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final String secretKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final HttpClient http;
    private final Sleeper sleeper;

    private final Payments payments = new Payments(this);
    private final Payouts payouts = new Payouts(this);
    private final BalanceResource balance = new BalanceResource(this);
    private final StoreResource store = new StoreResource(this);

    /** Client avec les réglages par défaut (30 s, 2 nouvelles tentatives). */
    public FomaPay(String secretKey) {
        this(builder(secretKey));
    }

    private FomaPay(Builder builder) {
        this.secretKey = builder.secretKey;
        this.baseUrl = builder.baseUrl.replaceAll("/+$", "");
        this.timeout = builder.timeout;
        this.maxRetries = Math.max(0, builder.maxRetries);
        this.http = builder.http != null ? builder.http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.sleeper = builder.sleeper;
    }

    /** @param secretKey clé secrète de la boutique (sk_test_… ou sk_live_…) */
    public static Builder builder(String secretKey) {
        if (secretKey == null || !(secretKey.startsWith("sk_test_") || secretKey.startsWith("sk_live_"))) {
            throw new FomaPayException(
                "ERROR_INVALID_API_KEY",
                "Clé secrète absente ou mal formée : elle commence par sk_test_ ou sk_live_ (FomaPay Business › Développeurs).");
        }
        return new Builder(secretKey);
    }

    public Payments payments() {
        return payments;
    }

    /** Alias de {@link #payments()}. */
    public Payments transactions() {
        return payments;
    }

    public Payouts payouts() {
        return payouts;
    }

    public BalanceResource balance() {
        return balance;
    }

    public StoreResource store() {
        return store;
    }

    public boolean isLivemode() {
        return secretKey.startsWith("sk_live_");
    }

    /** Clé unique : rejouer la requête ne crée pas de doublon. */
    static String newIdempotencyKey() {
        return "java-" + UUID.randomUUID().toString().replace("-", "");
    }

    static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Exécute une requête, avec nouvelles tentatives quand elle est rejouable sans risque
     * (lecture, ou POST accompagné d'une clé d'idempotence).
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> request(String method, String path, Map<String, Object> body, String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + secretKey)
            .header("Accept", "application/json")
            .header("User-Agent", "fomapay-java/" + VERSION);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpRequest request = builder.build();
        boolean replayable = "GET".equals(method) || idempotencyKey != null;

        for (int attempt = 0; ; attempt++) {
            FomaPayException error;
            boolean retry;
            long waitMs = -1;
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return parseObject(response.body(), status);
                }
                error = apiError(status, response.body());
                if (status == 429) {
                    // La gateway a refusé la requête avant tout traitement : toujours rejouable.
                    retry = true;
                    long retryAfter = response.headers().firstValue("Retry-After").filter(v -> v.matches("\\d{1,6}")).map(Long::parseLong).orElse(0L);
                    if (retryAfter > 0) {
                        waitMs = Math.min(retryAfter * 1000, MAX_BACKOFF_MS);
                    }
                } else {
                    retry = status >= 500 && replayable;
                }
            } catch (HttpTimeoutException e) {
                error = new FomaPayNetworkException("ERROR_TIMEOUT", "FomaPay n'a pas répondu dans le délai imparti.", e);
                retry = replayable;
            } catch (IOException e) {
                error = new FomaPayNetworkException("ERROR_NETWORK", "Impossible de joindre FomaPay : " + e.getMessage(), e);
                retry = replayable;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FomaPayNetworkException("ERROR_NETWORK", "Requête interrompue.", e);
            }

            if (!retry || attempt >= maxRetries) {
                throw error;
            }
            try {
                sleeper.sleep(waitMs >= 0 ? waitMs : backoff(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw error;
            }
        }
    }

    /** 0,5 s, 1 s, 2 s… plafonné à 8 s, avec un aléa pour désynchroniser les clients. */
    private static long backoff(int attempt) {
        long delay = Math.min(500L << Math.min(attempt, 10), MAX_BACKOFF_MS);
        return delay / 2 + ThreadLocalRandom.current().nextLong(delay / 2 + 1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String body, int status) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            if (Json.parse(body) instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (IllegalArgumentException ignored) {
            // traité ci-dessous
        }
        throw new FomaPayApiException("ERROR_INVALID_RESPONSE", "Réponse illisible de l'API FomaPay.", status, null);
    }

    @SuppressWarnings("unchecked")
    private static FomaPayApiException apiError(int status, String body) {
        Map<String, Object> error = Map.of();
        try {
            if (Json.parse(body) instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> e) {
                error = (Map<String, Object>) e;
            }
        } catch (IllegalArgumentException ignored) {
            // corps non JSON (proxy, page d'erreur…) : on garde le statut HTTP
        }
        return new FomaPayApiException(
            Objects.toString(error.get("code"), "ERROR_UNKNOWN"),
            Objects.toString(error.get("message"), "Erreur HTTP " + status),
            status,
            error.get("details") instanceof Map<?, ?> d ? (Map<String, Object>) d : null);
    }

    public static final class Builder {
        private final String secretKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private HttpClient http;
        private Sleeper sleeper = Thread::sleep;

        private Builder(String secretKey) {
            this.secretKey = secretKey;
        }

        /** À surcharger pour pointer vers un backend local en développement. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl);
            return this;
        }

        /** Délai maximal d'une requête (30 s par défaut). */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        /** Nouvelles tentatives après un échec rejouable (2 par défaut, 0 pour désactiver). */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Client HTTP à réutiliser (proxy, pool de connexions…). */
        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public FomaPay build() {
            return new FomaPay(this);
        }
    }
}
