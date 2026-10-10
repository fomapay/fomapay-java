package cd.fomapay.model;

import java.util.Map;

/** Événement de webhook : {@code payment.succeeded}, {@code payment.failed}, {@code payout.succeeded}… */
public final class Event extends ApiObject {
    public Event(Map<String, Object> raw) {
        super(raw);
    }

    public String getId() {
        return string("id");
    }

    public String getType() {
        return string("type");
    }

    public String getCreated() {
        return string("created");
    }

    public boolean isLivemode() {
        return bool("livemode");
    }

    /** Le paiement (ou retrait) concerné. */
    public Payment getData() {
        return new Payment(map("data"));
    }
}
