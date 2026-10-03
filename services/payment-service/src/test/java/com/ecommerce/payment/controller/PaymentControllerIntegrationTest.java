package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.model.PaymentAuthorization;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentAuthorizationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Charge rules against a real payment database: success, replay, conflict,
 * decline, outage with no row, void, and the user JWT.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentAuthorizationRepository paymentAuthorizationRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void resetDatabase() {
        paymentAuthorizationRepository.deleteAll();
    }

    @Test
    void authorize_newOrder_returns201AndStoresOneAuthorizedRow() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ResponseEntity<PaymentResponse> response = post(userId, orderId, "10.50", "USD", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body.id()).isNotNull();
        assertThat(body.orderId()).isEqualTo(orderId);
        assertThat(body.amount()).isEqualByComparingTo("10.50");
        assertThat(body.currency()).isEqualTo("USD");
        assertThat(body.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(paymentAuthorizationRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(body.id());
            assertThat(row.getOrderId()).isEqualTo(orderId);
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        });
    }

    @Test
    void authorize_sameUserAmountAndCurrency_returns200SameIdAndDoesNotInsertAgain() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ResponseEntity<PaymentResponse> created = post(userId, orderId, "10.50", "USD", null, null);
        ResponseEntity<PaymentResponse> replay = post(userId, orderId, "10.5", "usd", false, false);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(created.getBody().id());
        assertThat(replay.getBody().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(paymentAuthorizationRepository.count()).isEqualTo(1);
    }

    @Test
    void authorize_differentAmount_returns409AndLeavesTheRowUnchanged() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentResponse created = post(userId, orderId, "10.50", "USD", null, null).getBody();

        ResponseEntity<Map> response = post(userId, orderId, "11.00", "USD", null, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_CONFLICT");
        assertThat(response.getBody().get("timestamp")).isNotNull();
        PaymentAuthorization stored = paymentAuthorizationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(created.id());
        assertThat(stored.getAmount()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(paymentAuthorizationRepository.count()).isEqualTo(1);
    }

    @Test
    void authorize_differentCurrency_returns409AndLeavesTheRowUnchanged() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        post(userId, orderId, "10.50", "USD", null, null);

        ResponseEntity<Map> response = post(userId, orderId, "10.50", "EUR", null, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_CONFLICT");
        assertThat(paymentAuthorizationRepository.findByOrderId(orderId).orElseThrow().getCurrency()).isEqualTo("USD");
    }

    @Test
    void authorize_simulateDeclineWithNoRow_returns409AndStoresNothing() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ResponseEntity<Map> response = post(userId, orderId, "10.50", "USD", true, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_DECLINED");
        assertThat(response.getBody().get("message")).isEqualTo("Payment was declined");
        assertThat(paymentAuthorizationRepository.count()).isZero();
    }

    @Test
    void authorize_storedAuthorizedRowWinsOverLaterDecline() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentResponse created = post(userId, orderId, "10.50", "USD", null, null).getBody();

        ResponseEntity<PaymentResponse> replay = post(userId, orderId, "10.50", "USD", true, null);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(created.id());
        assertThat(replay.getBody().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(paymentAuthorizationRepository.count()).isEqualTo(1);
    }

    @Test
    void authorize_storedVoidedRowWinsOverLaterDecline() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentResponse created = post(userId, orderId, "10.50", "USD", null, null).getBody();
        assertThat(voidPayment(userId, orderId, PaymentResponse.class).getBody().status()).isEqualTo(PaymentStatus.VOIDED);

        ResponseEntity<PaymentResponse> replay = post(userId, orderId, "10.50", "USD", true, null);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(created.id());
        assertThat(replay.getBody().status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(paymentAuthorizationRepository.count()).isEqualTo(1);
    }

    @Test
    void authorize_simulateOutage_returns503AndStoresNothing() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ResponseEntity<Map> response = post(userId, orderId, "10.50", "USD", null, true, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_UNAVAILABLE");
        assertThat(response.getBody().get("message")).isEqualTo("Payment service is unavailable");
        assertThat(paymentAuthorizationRepository.count()).isZero();
    }

    @Test
    void authorize_simulateOutage_doesNotRewriteAnExistingRow() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        post(userId, orderId, "10.50", "USD", null, null);

        ResponseEntity<Map> response = post(userId, orderId, "10.50", "USD", null, true, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(paymentAuthorizationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(paymentAuthorizationRepository.count()).isEqualTo(1);
    }

    @Test
    void authorize_otherUser_returns403AndDoesNotRewrite() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentResponse created = post(owner, orderId, "10.50", "USD", null, null).getBody();

        ResponseEntity<Map> response = post(other, orderId, "99.00", "EUR", true, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_FORBIDDEN");
        PaymentAuthorization stored = paymentAuthorizationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(created.id());
        assertThat(stored.getUserId()).isEqualTo(owner);
        assertThat(stored.getAmount()).isEqualByComparingTo("10.50");
        assertThat(stored.getCurrency()).isEqualTo("USD");
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void authorize_missingBearer_returns401() {
        ResponseEntity<Map> response = exchange(
                "/api/v1/payments/authorizations",
                HttpMethod.POST,
                "{\"orderId\":\"" + UUID.randomUUID() + "\",\"amount\":10.50,\"currency\":\"USD\"}",
                Map.class,
                null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().get("timestamp")).isNotNull();
        assertThat(paymentAuthorizationRepository.count()).isZero();
    }

    @Test
    void void_authorizedBecomesVoided_andSecondVoidStaysVoided() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = post(userId, orderId, "10.50", "USD", null, null).getBody().id();

        ResponseEntity<PaymentResponse> first = voidPayment(userId, orderId, PaymentResponse.class);
        ResponseEntity<PaymentResponse> second = voidPayment(userId, orderId, PaymentResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().id()).isEqualTo(paymentId);
        assertThat(first.getBody().status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(paymentAuthorizationRepository.findAll()).singleElement()
                .extracting(PaymentAuthorization::getStatus)
                .isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void void_missingRow_returns200AndInsertsNothing() {
        ResponseEntity<String> response = voidPayment(UUID.randomUUID(), UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNullOrEmpty();
        assertThat(paymentAuthorizationRepository.count()).isZero();
    }

    @Test
    void void_otherUser_returns403AndLeavesTheRowAuthorized() {
        UUID owner = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        post(owner, orderId, "10.50", "USD", null, null);

        ResponseEntity<Map> response = voidPayment(UUID.randomUUID(), orderId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_FORBIDDEN");
        assertThat(paymentAuthorizationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);
    }

    private ResponseEntity<PaymentResponse> post(UUID userId, UUID orderId, String amount, String currency,
                                                  Boolean simulateDecline, Boolean simulateOutage) {
        return post(userId, orderId, amount, currency, simulateDecline, simulateOutage, PaymentResponse.class);
    }

    private <T> ResponseEntity<T> post(UUID userId, UUID orderId, String amount, String currency,
                                       Boolean simulateDecline, Boolean simulateOutage, Class<T> type) {
        String decline = simulateDecline == null ? "" : ",\"simulateDecline\":" + simulateDecline;
        String outage = simulateOutage == null ? "" : ",\"simulateOutage\":" + simulateOutage;
        String json = "{\"orderId\":\"" + orderId + "\",\"amount\":" + amount + ",\"currency\":\"" + currency + "\""
                + decline + outage + "}";
        return exchange("/api/v1/payments/authorizations", HttpMethod.POST, json, type, token(userId));
    }

    private <T> ResponseEntity<T> voidPayment(UUID userId, UUID orderId, Class<T> type) {
        return exchange("/api/v1/payments/authorizations/" + orderId + "/void", HttpMethod.POST, null, type, token(userId));
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, String body, Class<T> type, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), type);
    }

    private String token(UUID userId) {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", "user@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
