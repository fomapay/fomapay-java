package cd.fomapay;

import cd.fomapay.internal.Json;
import cd.fomapay.model.Event;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Vérification des webhooks FomaPay (HMAC-SHA256). */
public final class Webhook {
    /** En-tête HTTP qui porte la signature. */
    public static final String SIGNATURE_HEADER = "X-FomaPay-Signature";
    /** Âge maximal accepté pour un événement (anti-rejeu). */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private Webhook() {
    }

    /**
     * Vérifie la signature puis renvoie l'événement.
     *
     * @param payload corps BRUT de la requête, tel que reçu
     * @param header  valeur de l'en-tête {@code X-FomaPay-Signature}
     * @param secret  secret de signature de l'endpoint (whsec_…)
     * @throws FomaPaySignatureException si la signature est invalide ou expirée
     */
    public static Event constructEvent(String payload, String header, String secret) {
        return constructEvent(payload, header, secret, DEFAULT_TOLERANCE);
    }

    public static Event constructEvent(String payload, String header, String secret, Duration tolerance) {
        return constructEvent(payload, header, secret, tolerance, System.currentTimeMillis() / 1000);
    }

    @SuppressWarnings("unchecked")
    static Event constructEvent(String payload, String header, String secret, Duration tolerance, long now) {
        verify(payload, header, secret, tolerance, now);
        try {
            if (Json.parse(payload) instanceof Map<?, ?> map) {
                return new Event((Map<String, Object>) map);
            }
        } catch (IllegalArgumentException ignored) {
            // traité ci-dessous
        }
        throw new FomaPaySignatureException("ERROR_INVALID_PAYLOAD", "Le corps du webhook n'est pas un événement FomaPay valide.");
    }

    /**
     * Contrôle la signature d'un webhook.
     *
     * @throws FomaPaySignatureException si la signature est invalide ou expirée
     */
    public static void verify(String payload, String header, String secret) {
        verify(payload, header, secret, DEFAULT_TOLERANCE);
    }

    public static void verify(String payload, String header, String secret, Duration tolerance) {
        verify(payload, header, secret, tolerance, System.currentTimeMillis() / 1000);
    }

    /** Variante sans exception, pratique dans un filtre HTTP. */
    public static boolean isValid(String payload, String header, String secret) {
        try {
            verify(payload, header, secret);
            return true;
        } catch (FomaPaySignatureException e) {
            return false;
        }
    }

    static void verify(String payload, String header, String secret, Duration tolerance, long now) {
        if (secret == null || secret.isEmpty()) {
            throw new FomaPayException("ERROR_WEBHOOK_SECRET_MISSING", "Le secret de signature du webhook (whsec_…) est requis.");
        }
        String timestamp = "";
        String signature = "";
        for (String part : (header == null ? "" : header).split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                timestamp = pair[1];
            } else if (pair.length == 2 && pair[0].equals("v1")) {
                signature = pair[1];
            }
        }
        if (!timestamp.matches("\\d{1,18}") || signature.isEmpty() || payload == null) {
            throw new FomaPaySignatureException("ERROR_INVALID_SIGNATURE", "En-tête de signature absent ou mal formé.");
        }

        // Comparaison en temps constant.
        byte[] expected = hmacHex(secret, timestamp + "." + payload).getBytes(StandardCharsets.US_ASCII);
        byte[] given = signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, given)) {
            throw new FomaPaySignatureException("ERROR_INVALID_SIGNATURE", "La signature ne correspond pas au contenu reçu.");
        }

        // L'horodatage n'est contrôlé qu'après la signature : il est alors authentique.
        if (Math.abs(now - Long.parseLong(timestamp)) > tolerance.getSeconds()) {
            throw new FomaPaySignatureException("ERROR_INVALID_SIGNATURE", "Événement trop ancien : rejeu refusé.");
        }
    }

    static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponible sur cette JVM", e);
        }
    }
}
