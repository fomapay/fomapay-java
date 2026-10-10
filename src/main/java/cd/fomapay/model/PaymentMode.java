package cd.fomapay.model;

/** Modes de paiement proposés au client. */
public enum PaymentMode {
    /** Portefeuille FomaPay (QR / code marchand). Disponible dès le Niveau 0. */
    FOMAPAY_DIRECT("fomapay_direct"),
    /** M-Pesa, Orange Money, Airtel Money. Réservé aux boutiques vérifiées (Niveau 1+). */
    MOBILE_MONEY("mobile_money");

    private final String value;

    PaymentMode(String value) {
        this.value = value;
    }

    /** Valeur envoyée à l'API. */
    public String value() {
        return value;
    }
}
