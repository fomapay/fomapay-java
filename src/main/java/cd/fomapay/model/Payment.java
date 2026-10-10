package cd.fomapay.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Un paiement, un retrait ou tout autre mouvement. */
public final class Payment extends ApiObject {
    public Payment(Map<String, Object> raw) {
        super(raw);
    }

    public String getId() {
        return string("id");
    }

    /** Référence lisible (FP-…), acceptée partout à la place de l'identifiant. */
    public String getReference() {
        return string("reference");
    }

    /** {@code pending}, {@code processing}, {@code success}, {@code failed} ou {@code expired}. */
    public String getStatus() {
        return string("status");
    }

    public boolean isSuccessful() {
        return "success".equals(getStatus());
    }

    /** Le paiement attend encore le client ou l'opérateur. */
    public boolean isPending() {
        return "pending".equals(getStatus()) || "processing".equals(getStatus());
    }

    public BigDecimal getAmount() {
        return decimal("amount");
    }

    public BigDecimal getFee() {
        return decimal("fee");
    }

    public BigDecimal getNet() {
        return decimal("net");
    }

    public String getCurrency() {
        return string("currency");
    }

    public String getChannel() {
        return string("channel");
    }

    public String getOrderId() {
        return string("orderId");
    }

    public String getDescription() {
        return string("description");
    }

    public Map<String, Object> getCustomer() {
        return map("customer");
    }

    public Map<String, Object> getMetadata() {
        return map("metadata");
    }

    public List<String> getPaymentModes() {
        return strings("paymentModes");
    }

    public String getCallbackUrl() {
        return string("callbackUrl");
    }

    public String getPayerPhone() {
        return string("payerPhone");
    }

    /** Contenu du QR code à afficher au client (FomaPay Direct). */
    public String getQrCode() {
        return string("qrCode");
    }

    public boolean isLivemode() {
        return bool("livemode");
    }

    public String getStoreId() {
        return string("storeId");
    }

    public String getFailureCode() {
        return string("failureCode");
    }

    public String getFailureReason() {
        return string("failureReason");
    }

    public String getExpiresAt() {
        return string("expiresAt");
    }

    public String getCompletedAt() {
        return string("completedAt");
    }

    public String getCreatedAt() {
        return string("createdAt");
    }
}
