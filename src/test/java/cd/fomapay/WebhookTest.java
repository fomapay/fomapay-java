package cd.fomapay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cd.fomapay.model.Event;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WebhookTest {
    private static final String SECRET = "whsec_unit";
    private static final long NOW = 1_800_000_000L;
    private static final Duration TOLERANCE = Webhook.DEFAULT_TOLERANCE;
    private static final String BODY =
        "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"created\":\"2026-10-10T10:00:00Z\",\"livemode\":false,"
            + "\"data\":{\"id\":\"pay_1\",\"status\":\"success\",\"orderId\":\"CMD-1\"}}";

    private static String sign(String body, String secret, long timestamp) {
        return "t=" + timestamp + ",v1=" + Webhook.hmacHex(secret, timestamp + "." + body);
    }

    @Test
    void validSignatureReturnsTheEvent() {
        Event event = Webhook.constructEvent(BODY, sign(BODY, SECRET, NOW), SECRET, TOLERANCE, NOW);
        assertEquals("payment.succeeded", event.getType());
        assertTrue(event.getData().isSuccessful());
        assertEquals("CMD-1", event.getData().getOrderId());
        assertDoesNotThrow(() -> Webhook.verify(BODY, sign(BODY, SECRET, NOW), SECRET, TOLERANCE, NOW + 299));
    }

    @Test
    void knownVector() {
        // Vecteur commun à tous les SDKs FomaPay, calculé avec OpenSSL :
        // printf '1700000000.{"id":"evt_1"}' | openssl dgst -sha256 -hmac whsec_test
        String header = "t=1700000000,v1=c89214b5b5da833daed6f0b8c5bb6bd58cea9022bd80ccc78230f3942d632925";
        assertDoesNotThrow(() -> Webhook.verify("{\"id\":\"evt_1\"}", header.toUpperCase().replace("T=", "t=").replace("V1=", "v1="), "whsec_test", TOLERANCE, 1_700_000_000L));
    }

    @Test
    void invalidSignaturesAreRejected() {
        String valid = sign(BODY, SECRET, NOW);
        Object[][] cases = {
            {BODY + " ", valid, NOW},                              // corps modifié
            {BODY, sign(BODY, "whsec_autre", NOW), NOW},           // mauvais secret
            {BODY, valid, NOW + 301},                              // trop ancien
            {BODY, valid, NOW - 301},                              // horodatage dans le futur
            {BODY, "", NOW},
            {BODY, null, NOW},
            {BODY, "t=abc,v1=00", NOW},
            {BODY, "t=" + NOW, NOW},
        };
        for (Object[] c : cases) {
            FomaPaySignatureException e = assertThrows(
                FomaPaySignatureException.class,
                () -> Webhook.constructEvent((String) c[0], (String) c[1], SECRET, TOLERANCE, (long) c[2]));
            assertEquals("ERROR_INVALID_SIGNATURE", e.getCode());
        }
        assertFalse(Webhook.isValid(BODY, "t=1,v1=00", SECRET));
    }

    @Test
    void missingSecretIsReported() {
        FomaPayException e = assertThrows(FomaPayException.class, () -> Webhook.verify(BODY, sign(BODY, SECRET, NOW), ""));
        assertEquals("ERROR_WEBHOOK_SECRET_MISSING", e.getCode());
    }
}
