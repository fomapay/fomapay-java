package cd.fomapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cd.fomapay.internal.Json;
import cd.fomapay.model.Currency;
import cd.fomapay.model.Operator;
import cd.fomapay.model.Payment;
import cd.fomapay.model.PaymentMode;
import cd.fomapay.model.PaymentParams;
import cd.fomapay.model.PayoutParams;
import cd.fomapay.model.Store;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FomaPayTest {
    private static final String PAYMENT = "{\"id\":\"pay_1\",\"reference\":\"FP-1\",\"status\":\"pending\",\"amount\":25,\"currency\":\"USD\",\"orderId\":\"CMD-1\",\"champInconnu\":1}";
    private static final PayoutParams PAYOUT =
        PayoutParams.builder().amount(10).currency(Currency.USD).operator(Operator.MPESA).phone("+243810000000").build();

    /** Requête reçue par le faux serveur. */
    private record Call(String method, String path, String body, String authorization, String idempotencyKey) {
    }

    /** Réponse à renvoyer pour le n-ième appel (à partir de 1). */
    private interface Responder {
        void respond(int n, HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final List<Long> waits = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private FomaPay.Builder client(Responder responder) {
        server.createContext("/", exchange -> {
            calls.add(new Call(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath() + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key")));
            responder.respond(calls.size(), exchange);
            exchange.close();
        });
        return FomaPay.builder("sk_test_unit")
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
            .sleeper(waits::add);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void fail(HttpExchange exchange, int status, String code) throws IOException {
        send(exchange, status, "{\"error\":{\"code\":\"" + code + "\",\"message\":\"oups\",\"details\":{\"champ\":\"amount\"}}}");
    }

    @Test
    void rejectsMalformedSecretKey() {
        FomaPayException e = assertThrows(FomaPayException.class, () -> new FomaPay("pk_test_abc"));
        assertEquals("ERROR_INVALID_API_KEY", e.getCode());
    }

    @Test
    void createSendsStandardFormatAndIdempotencyKey() {
        FomaPay fomapay = client((n, exchange) -> send(exchange, 201, PAYMENT)).build();

        Payment payment = fomapay.transactions().create(PaymentParams.builder()
            .amount(25)
            .currency(Currency.USD)
            .orderId("CMD-1")
            .customer("Jean Marc", "+243810000000", null)
            .paymentModes(PaymentMode.FOMAPAY_DIRECT, PaymentMode.MOBILE_MONEY)
            .callbackUrl("https://marchand.cd/api/callback")
            .metadata("custom_field", "valeur")
            .build());

        Call call = calls.get(0);
        assertEquals("POST /v1/payments", call.method() + " " + call.path());
        assertEquals("Bearer sk_test_unit", call.authorization());
        assertTrue(call.idempotencyKey().startsWith("java-"));
        assertEquals(
            Json.parse("{\"amount\":25,\"currency\":\"USD\",\"orderId\":\"CMD-1\",\"customer\":{\"name\":\"Jean Marc\",\"phone\":\"+243810000000\"},"
                + "\"paymentModes\":[\"fomapay_direct\",\"mobile_money\"],\"callbackUrl\":\"https://marchand.cd/api/callback\","
                + "\"metadata\":{\"custom_field\":\"valeur\"}}"),
            Json.parse(call.body()));
        assertEquals("CMD-1", payment.getOrderId());
        assertEquals(0, payment.getAmount().compareTo(BigDecimal.valueOf(25)));
        assertTrue(payment.isPending());
        assertFalse(payment.isSuccessful());
        assertEquals(BigDecimal.ONE, payment.getRaw().get("champInconnu"));
    }

    @Test
    void retriesServerErrorsWithTheSameIdempotencyKey() {
        FomaPay fomapay = client((n, exchange) -> {
            if (n < 3) {
                fail(exchange, 503, "ERROR_UNAVAILABLE");
            } else {
                send(exchange, 201, PAYMENT);
            }
        }).build();

        assertEquals("pay_1", fomapay.payments().create(PaymentParams.builder().amount(25).currency(Currency.USD).build()).getId());
        assertEquals(3, calls.size());
        assertEquals(1, calls.stream().map(Call::idempotencyKey).distinct().count());
        assertEquals(2, waits.size());
        assertTrue(waits.get(0) >= 250 && waits.get(0) <= 500 && waits.get(1) >= 500 && waits.get(1) <= 1000, waits.toString());
    }

    @Test
    void givesUpAfterMaxRetries() {
        FomaPay fomapay = client((n, exchange) -> fail(exchange, 500, "ERROR_INTERNAL")).maxRetries(1).build();
        FomaPayApiException e = assertThrows(FomaPayApiException.class, () -> fomapay.payments().retrieve("FP-1"));
        assertEquals(500, e.getStatus());
        assertEquals("ERROR_INTERNAL", e.getCode());
        assertEquals(2, calls.size());
    }

    @Test
    void clientErrorsAreNotRetried() {
        FomaPay fomapay = client((n, exchange) -> fail(exchange, 403, "ERROR_LEVEL_0_LIMIT_REACHED")).build();
        FomaPayApiException e = assertThrows(
            FomaPayApiException.class,
            () -> fomapay.payments().create(PaymentParams.builder().amount(900).currency(Currency.USD).build()));
        assertEquals("ERROR_LEVEL_0_LIMIT_REACHED", e.getCode());
        assertEquals(Map.of("champ", "amount"), e.getDetails());
        assertEquals(1, calls.size());
    }

    @Test
    void payoutsAreNeverReplayed() {
        FomaPay fomapay = client((n, exchange) -> fail(exchange, 502, "ERROR_BAD_GATEWAY")).build();
        assertThrows(FomaPayApiException.class, () -> fomapay.payouts().create(PAYOUT));
        assertEquals(1, calls.size());
        assertNull(calls.get(0).idempotencyKey());
        assertEquals(
            Json.parse("{\"amount\":10,\"currency\":\"USD\",\"operator\":\"mpesa\",\"phone\":\"+243810000000\"}"),
            Json.parse(calls.get(0).body()));
    }

    @Test
    void rateLimitIsAlwaysRetriedAndHonoursRetryAfter() {
        FomaPay fomapay = client((n, exchange) -> {
            if (n == 1) {
                exchange.getResponseHeaders().add("Retry-After", "2");
                fail(exchange, 429, "ERROR_RATE_LIMITED");
            } else {
                send(exchange, 201, PAYMENT);
            }
        }).build();

        fomapay.payouts().create(PAYOUT);
        assertEquals(2, calls.size());
        assertEquals(List.of(2000L), waits);
    }

    @Test
    void timeoutsAndNetworkErrors() {
        FomaPay slow = client((n, exchange) -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            send(exchange, 200, "{\"data\":[]}");
        }).timeout(Duration.ofMillis(100)).maxRetries(1).build();
        FomaPayNetworkException timeout = assertThrows(FomaPayNetworkException.class, () -> slow.balance().retrieve());
        assertEquals("ERROR_TIMEOUT", timeout.getCode());
        assertEquals(0, timeout.getStatus());
        assertEquals(1, waits.size());

        FomaPay offline = FomaPay.builder("sk_test_unit").baseUrl("http://127.0.0.1:1").sleeper(waits::add).build();
        assertEquals("ERROR_NETWORK", assertThrows(FomaPayNetworkException.class, () -> offline.payouts().create(PAYOUT)).getCode());
    }

    @Test
    void otherResources() {
        FomaPay fomapay = client((n, exchange) -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/v1/payments")) {
                send(exchange, 200, "{\"data\":[" + PAYMENT + "]}");
            } else if (path.equals("/v1/balance")) {
                send(exchange, 200, "{\"data\":[{\"currency\":\"USD\",\"available\":12.5}]}");
            } else if (path.equals("/v1/store")) {
                send(exchange, 200, "{\"id\":\"s1\",\"name\":\"Chez Foma\",\"shortCode\":\"FOMA\",\"kycLevel\":2,\"paymentModes\":[\"fomapay_direct\"]}");
            } else {
                send(exchange, 200, PAYMENT.replace("pending", "success"));
            }
        }).build();

        assertEquals(1, fomapay.payments().list(5, "success").size());
        assertEquals(new BigDecimal("12.5"), fomapay.balance().retrieve().get(0).getAvailable());
        Store store = fomapay.store().retrieve();
        assertEquals("FOMA", store.getShortCode());
        assertEquals(2, store.getKycLevel());
        assertEquals(List.of("fomapay_direct"), store.getPaymentModes());
        fomapay.payments().payWithMobileMoney("FP/1", Operator.AIRTEL_MONEY, "+243970000000");
        assertTrue(fomapay.payments().simulate("pay_1", "success").isSuccessful());

        assertEquals("/v1/payments?limit=5&status=success", calls.get(0).path());
        assertEquals("/v1/payments/FP%2F1/mobile-money", calls.get(3).path());
        assertEquals(Json.parse("{\"outcome\":\"success\"}"), Json.parse(calls.get(4).body()));
        assertFalse(fomapay.isLivemode());
    }

    @Test
    void jsonRoundTrip() {
        String text = "{\"a\":[1,2.5,true,null,\"é\\n\\\"x\\\"\\u00e9\"],\"b\":{}}";
        assertEquals(Json.parse(text), Json.parse(Json.stringify(Json.parse(text))));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("<html>"));
    }
}
