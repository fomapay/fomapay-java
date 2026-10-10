package cd.fomapay;

import cd.fomapay.model.Store;

/** Boutique liée à la clé. Obtenu par {@link FomaPay#store()}. */
public final class StoreResource {
    private final FomaPay client;

    StoreResource(FomaPay client) {
        this.client = client;
    }

    public Store retrieve() {
        return new Store(client.request("GET", "/v1/store", null, null));
    }
}
