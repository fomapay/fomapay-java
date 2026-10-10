package cd.fomapay;

import cd.fomapay.model.Operator;
import cd.fomapay.model.Payment;
import cd.fomapay.model.PaymentParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Paiements de la boutique. Obtenu par {@link FomaPay#payments()}. */
public final class Payments {
    private final FomaPay client;

    Payments(FomaPay client) {
        this.client = client;
    }

    /** Crée une demande de paiement. Porte toujours une clé d'idempotence : rejouable sans doublon. */
    public Payment create(PaymentParams params) {
        String key = params.getIdempotencyKey() != null ? params.getIdempotencyKey() : FomaPay.newIdempotencyKey();
        return new Payment(client.request("POST", "/v1/payments", params.toMap(), key));
    }

    /** Relit un paiement par son identifiant ou sa référence (FP-…). */
    public Payment retrieve(String id) {
        return new Payment(client.request("GET", "/v1/payments/" + FomaPay.encode(id), null, null));
    }

    /** Les derniers paiements de la boutique. */
    public List<Payment> list() {
        return list(0, null);
    }

    /**
     * @param limit  nombre maximal de résultats (0 : valeur par défaut de l'API)
     * @param status filtre sur le statut, ou {@code null}
     */
    @SuppressWarnings("unchecked")
    public List<Payment> list(int limit, String status) {
        List<String> query = new ArrayList<>();
        if (limit > 0) {
            query.add("limit=" + limit);
        }
        if (status != null) {
            query.add("status=" + FomaPay.encode(status));
        }
        Map<String, Object> data = client.request("GET", "/v1/payments" + (query.isEmpty() ? "" : "?" + String.join("&", query)), null, null);
        List<Payment> payments = new ArrayList<>();
        if (data.get("data") instanceof List<?> items) {
            for (Object item : items) {
                payments.add(new Payment((Map<String, Object>) item));
            }
        }
        return payments;
    }

    /** Envoie la demande de débit (push USSD) sur le téléphone du client. */
    public Payment payWithMobileMoney(String id, Operator operator, String phone) {
        Map<String, Object> body = Map.of("operator", operator.value(), "phone", phone);
        return new Payment(client.request("POST", "/v1/payments/" + FomaPay.encode(id) + "/mobile-money", body, null));
    }

    /** Force l'issue d'un paiement ({@code success} ou {@code failed}). Sandbox uniquement. */
    public Payment simulate(String id, String outcome) {
        return new Payment(client.request("POST", "/v1/payments/" + FomaPay.encode(id) + "/simulate", Map.of("outcome", outcome), null));
    }
}
