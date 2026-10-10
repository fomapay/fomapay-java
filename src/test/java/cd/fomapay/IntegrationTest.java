package cd.fomapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cd.fomapay.model.Currency;
import cd.fomapay.model.Operator;
import cd.fomapay.model.Payment;
import cd.fomapay.model.PaymentMode;
import cd.fomapay.model.PaymentParams;
import cd.fomapay.model.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests d'intégration contre le sandbox FomaPay. Ils ne s'exécutent que si
 * FOMAPAY_TEST_SECRET_KEY (sk_test_…) est définie ; FOMAPAY_API_URL cible un backend local :
 *
 * <pre>FOMAPAY_API_URL=http://localhost:8080 FOMAPAY_TEST_SECRET_KEY=sk_test_… mvn test</pre>
 */
@EnabledIfEnvironmentVariable(named = "FOMAPAY_TEST_SECRET_KEY", matches = "sk_test_.+")
class IntegrationTest {
    @Test
    void sandbox() throws InterruptedException {
        FomaPay.Builder builder = FomaPay.builder(System.getenv("FOMAPAY_TEST_SECRET_KEY"));
        String baseUrl = System.getenv("FOMAPAY_API_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        FomaPay fomapay = builder.build();

        Store store = fomapay.store().retrieve();
        assertFalse(store.isLivemode());
        assertEquals(2, store.getPaymentModes().size());

        // Format standard d'une transaction, dans les deux modes.
        Payment payment = fomapay.payments().create(PaymentParams.builder()
            .amount(1000)
            .currency(Currency.CDF)
            .orderId("CMD-2026-001")
            .customer("Jean Marc", "+243810000000", "jean@example.com")
            .paymentModes(PaymentMode.FOMAPAY_DIRECT, PaymentMode.MOBILE_MONEY)
            .callbackUrl("https://marchand.cd/api/callback")
            .metadata("custom_field", "valeur")
            .build());
        assertEquals("pending", payment.getStatus());
        assertEquals("https://marchand.cd/api/callback", payment.getCallbackUrl());
        assertNotNull(payment.getQrCode());

        // Mode Mobile Money : l'opérateur simulé confirme après ~3 s.
        Payment last = fomapay.payments().payWithMobileMoney(payment.getId(), Operator.AIRTEL_MONEY, "+243970000000");
        assertEquals("processing", last.getStatus());
        long deadline = System.currentTimeMillis() + 10_000;
        while (last.isPending()) {
            assertTrue(System.currentTimeMillis() < deadline, "le paiement Mobile Money n'a pas été confirmé à temps");
            Thread.sleep(1000);
            last = fomapay.payments().retrieve(payment.getReference());
        }
        assertTrue(last.isSuccessful());
        assertEquals("airtel_money", last.getChannel());

        // Mode FomaPay Direct : issue forcée en sandbox.
        Payment direct = fomapay.payments().create(
            PaymentParams.builder().amount(12.5).currency(Currency.USD).paymentModes(PaymentMode.FOMAPAY_DIRECT).build());
        assertTrue(fomapay.payments().simulate(direct.getId(), "success").isSuccessful());

        // Idempotence : la même clé renvoie le même paiement.
        PaymentParams same = PaymentParams.builder().amount(3).currency(Currency.USD).idempotencyKey("java-it-" + payment.getId()).build();
        assertEquals(fomapay.payments().create(same).getId(), fomapay.payments().create(same).getId());

        assertFalse(fomapay.payments().list(5, "success").isEmpty());
        assertFalse(fomapay.balance().retrieve().isEmpty());

        FomaPayApiException invalid = assertThrows(
            FomaPayApiException.class,
            () -> fomapay.payments().create(PaymentParams.builder().amount(-1).currency(Currency.USD).build()));
        assertEquals("ERROR_VALIDATION", invalid.getCode());
        FomaPayApiException missing = assertThrows(FomaPayApiException.class, () -> fomapay.payments().retrieve("FP-INEXISTANT"));
        assertEquals("ERROR_NOT_FOUND", missing.getCode());
        assertEquals(404, missing.getStatus());
    }
}
