package cd.fomapay.model;

/** Opérateurs Mobile Money. */
public enum Operator {
    MPESA("mpesa"),
    ORANGE_MONEY("orange_money"),
    AIRTEL_MONEY("airtel_money");

    private final String value;

    Operator(String value) {
        this.value = value;
    }

    /** Valeur envoyée à l'API. */
    public String value() {
        return value;
    }
}
