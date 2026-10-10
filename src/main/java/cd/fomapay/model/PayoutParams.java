package cd.fomapay.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Paramètres d'un retrait du solde de la boutique vers un compte Mobile Money. */
public final class PayoutParams {
    private final Map<String, Object> body;

    private PayoutParams(Map<String, Object> body) {
        this.body = body;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Corps JSON envoyé à l'API. */
    public Map<String, Object> toMap() {
        return body;
    }

    public static final class Builder {
        private BigDecimal amount;
        private Currency currency;
        private Operator operator;
        private String phone;
        private String description;

        private Builder() {
        }

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

        public Builder operator(Operator operator) {
            this.operator = operator;
            return this;
        }

        /** Numéro bénéficiaire au format international (+243…). */
        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public PayoutParams build() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", Objects.requireNonNull(amount, "amount est requis"));
            body.put("currency", Objects.requireNonNull(currency, "currency est requis").name());
            body.put("operator", Objects.requireNonNull(operator, "operator est requis").value());
            body.put("phone", Objects.requireNonNull(phone, "phone est requis"));
            if (description != null) {
                body.put("description", description);
            }
            return new PayoutParams(body);
        }
    }
}
