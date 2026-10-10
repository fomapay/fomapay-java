package cd.fomapay.model;

import java.util.List;
import java.util.Map;

/** Boutique liée à la clé, avec les modes que son niveau KYC autorise. */
public final class Store extends ApiObject {
    public Store(Map<String, Object> raw) {
        super(raw);
    }

    public String getId() {
        return string("id");
    }

    public String getName() {
        return string("name");
    }

    public String getShortCode() {
        return string("shortCode");
    }

    public String getLogoUrl() {
        return string("logoUrl");
    }

    public boolean isLivemode() {
        return bool("livemode");
    }

    public int getKycLevel() {
        return decimal("kycLevel").intValue();
    }

    public List<String> getPaymentModes() {
        return strings("paymentModes");
    }
}
