package cd.fomapay;

import cd.fomapay.model.Balance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Solde de la boutique. Obtenu par {@link FomaPay#balance()}. */
public final class BalanceResource {
    private final FomaPay client;

    BalanceResource(FomaPay client) {
        this.client = client;
    }

    /** Un solde par devise. */
    @SuppressWarnings("unchecked")
    public List<Balance> retrieve() {
        List<Balance> balances = new ArrayList<>();
        if (client.request("GET", "/v1/balance", null, null).get("data") instanceof List<?> items) {
            for (Object item : items) {
                balances.add(new Balance((Map<String, Object>) item));
            }
        }
        return balances;
    }
}
