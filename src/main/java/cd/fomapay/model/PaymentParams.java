package cd.fomapay.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Paramètres de création d'un paiement.
 *
 * <pre>{@code
 * PaymentParams.builder()
 *     .amount(1000).currency(Currency.CDF)
 *     .orderId("CMD-2026-001")
 *     .customer("Jean Marc", "+243810000000", "jean@example.com")
 *     .paymentModes(PaymentMode.FOMAPAY_DIRECT, PaymentMode.MOBILE_MONEY)
 *     .callbackUrl("https://marchand.cd/api/callback")
 *     .metadata("custom_field", "valeur")
 *     .build();
 * }</pre>
 */
public final class PaymentParams {
    private final Map<String, Object> body;
    private final String idempotencyKey;

    private PaymentParams(Map<String, Object> body, String idempotencyKey) {
        this.body = body;
        this.idempotencyKey = idempotencyKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Corps JSON envoyé à l'API. */
    public Map<String, Object> toMap() {
        return body;
    }

    /** Clé d'idempotence choisie par l'appelant, ou {@code null}. */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public static final class Builder {
        private BigDecimal amount;
        private Currency currency;
        private String orderId;
        private String description;
        private final Map<String, Object> customer = new LinkedHashMap<>();
        private final List<String> paymentModes = new ArrayList<>();
        private String callbackUrl;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private String idempotencyKey;

        private Builder() {
        }

        /** Montant en unités de la devise (12.50 = 12,50 USD). */
        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder amount(double amount) {
            return amount(BigDecimal.valueOf(amount));
        }

        public Builder amount(long amount) {
            return amount(BigDecimal.valueOf(amount));
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        /** Votre identifiant de commande, renvoyé tel quel dans les webhooks. */
        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Client final ; passez {@code null} pour un champ inconnu. */
        public Builder customer(String name, String phone, String email) {
            customer.clear();
            putIfPresent(customer, "name", name);
            putIfPresent(customer, "phone", phone);
            putIfPresent(customer, "email", email);
            return this;
        }

        public Builder paymentModes(PaymentMode... modes) {
            paymentModes.clear();
            for (PaymentMode mode : modes) {
                paymentModes.add(mode.value());
            }
            return this;
        }

        /** Page de votre site vers laquelle renvoyer le client une fois le paiement terminé. */
        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder metadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, ?> values) {
            metadata.putAll(values);
            return this;
        }

        /** Ex. l'identifiant de commande : un appel répété renvoie le même paiement. */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public PaymentParams build() {
            Objects.requireNonNull(amount, "amount est requis");
            Objects.requireNonNull(currency, "currency est requis");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amount);
            body.put("currency", currency.name());
            putIfPresent(body, "orderId", orderId);
            putIfPresent(body, "description", description);
            if (!customer.isEmpty()) {
                body.put("customer", new LinkedHashMap<>(customer));
            }
            if (!paymentModes.isEmpty()) {
                body.put("paymentModes", List.copyOf(paymentModes));
            }
            putIfPresent(body, "callbackUrl", callbackUrl);
            if (!metadata.isEmpty()) {
                body.put("metadata", new LinkedHashMap<>(metadata));
            }
            return new PaymentParams(body, idempotencyKey);
        }

        private static void putIfPresent(Map<String, Object> map, String key, Object value) {
            if (value != null) {
                map.put(key, value);
            }
        }
    }
}
