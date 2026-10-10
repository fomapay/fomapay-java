package cd.fomapay;

import cd.fomapay.model.Payment;
import cd.fomapay.model.PayoutParams;

/** Retraits vers Mobile Money. Obtenu par {@link FomaPay#payouts()}. */
public final class Payouts {
    private final FomaPay client;

    Payouts(FomaPay client) {
        this.client = client;
    }

    /**
     * Retrait du solde de la boutique vers un compte Mobile Money.
     * Jamais rejoué automatiquement après une erreur réseau : un retrait ne doit pas partir deux fois.
     */
    public Payment create(PayoutParams params) {
        return new Payment(client.request("POST", "/v1/payouts", params.toMap(), null));
    }
}
