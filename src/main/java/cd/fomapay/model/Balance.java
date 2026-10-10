package cd.fomapay.model;

import java.math.BigDecimal;
import java.util.Map;

/** Solde disponible de la boutique dans une devise. */
public final class Balance extends ApiObject {
    public Balance(Map<String, Object> raw) {
        super(raw);
    }

    public String getCurrency() {
        return string("currency");
    }

    public BigDecimal getAvailable() {
        return decimal("available");
    }
}
