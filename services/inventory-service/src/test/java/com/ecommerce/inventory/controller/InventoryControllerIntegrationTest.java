package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.model.Stock;
import com.ecommerce.inventory.repository.StockRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed coverage of hold, insufficient stock, release,
 * commit, and revert against a real PostgreSQL instance.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StockRepository stockRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void useApacheHttpClientRequestFactory() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/inventory";
    }

    @Test
    void upsert_isOpen_andNegativeAvailableIs400() {
        UUID productId = UUID.randomUUID();

        ResponseEntity<StockResponse> upserted = exchange(
                baseUrl() + "/stock/" + productId, HttpMethod.PUT, Map.of("available", 9), StockResponse.class, null);

        assertThat(upserted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upserted.getBody().available()).isEqualTo(9);
        assertThat(upserted.getBody().reserved()).isZero();

        ResponseEntity<StockResponse> fetched = exchange(
                baseUrl() + "/stock/" + productId, HttpMethod.GET, null, StockResponse.class, null);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().available()).isEqualTo(9);

        ResponseEntity<Map> negative = exchange(
                baseUrl() + "/stock/" + productId, HttpMethod.PUT, Map.of("available", -1), Map.class, null);
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negative.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(stockRepository.findById(productId).orElseThrow().getAvailable()).isEqualTo(9);
    }

    @Test
    void hold_thenRelease_restoresAvailable_andReplayDoesNotDoubleHold() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        seed(productId, 10);
        String token = token(UUID.randomUUID());

        ResponseEntity<Map> anonymous = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, reserveBody(orderId, productId, 4), Map.class, null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody().get("code")).isEqualTo("UNAUTHORIZED");

        ResponseEntity<ReservationResponse> held = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, reserveBody(orderId, productId, 4),
                ReservationResponse.class, token);
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(held.getBody().status()).isEqualTo(ReservationStatus.HELD);
        assertStock(productId, 6, 4);

        ResponseEntity<ReservationResponse> replay = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, reserveBody(orderId, productId, 4),
                ReservationResponse.class, token);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(held.getBody().id());
        assertStock(productId, 6, 4);

        ResponseEntity<ReservationResponse> released = exchange(
                baseUrl() + "/reservations/" + held.getBody().id() + "/release",
                HttpMethod.POST, null, ReservationResponse.class, token);
        assertThat(released.getBody().status()).isEqualTo(ReservationStatus.RELEASED);
        assertStock(productId, 10, 0);

        exchange(baseUrl() + "/reservations/" + held.getBody().id() + "/release",
                HttpMethod.POST, null, ReservationResponse.class, token);
        assertStock(productId, 10, 0);

        long versionAfter = stockRepository.findById(productId).orElseThrow().getVersion();
        assertThat(versionAfter).isGreaterThan(0);
    }

    @Test
    void hold_duplicateProductLines_mergeBeforeReserving() {
        UUID productId = UUID.randomUUID();
        seed(productId, 5);
        String token = token(UUID.randomUUID());

        Map<String, Object> tooMany = Map.of(
                "orderId", UUID.randomUUID().toString(),
                "lines", java.util.List.of(
                        Map.of("productId", productId.toString(), "quantity", 3),
                        Map.of("productId", productId.toString(), "quantity", 3)));
        ResponseEntity<Map> rejected = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, tooMany, Map.class, token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().get("code")).isEqualTo("INSUFFICIENT_STOCK");
        assertStock(productId, 5, 0);

        Map<String, Object> within = Map.of(
                "orderId", UUID.randomUUID().toString(),
                "lines", java.util.List.of(
                        Map.of("productId", productId.toString(), "quantity", 2),
                        Map.of("productId", productId.toString(), "quantity", 2)));
        ResponseEntity<ReservationResponse> held = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, within, ReservationResponse.class, token);
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(held.getBody().lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(4));
        assertStock(productId, 1, 4);
    }

    @Test
    void hold_whenOneLineIsShort_reservesNothing() {
        UUID enough = UUID.randomUUID();
        UUID shortId = UUID.randomUUID();
        seed(enough, 5);
        seed(shortId, 1);
        Map<String, Object> body = Map.of(
                "orderId", UUID.randomUUID().toString(),
                "lines", java.util.List.of(
                        Map.of("productId", enough.toString(), "quantity", 3),
                        Map.of("productId", shortId.toString(), "quantity", 2)));

        ResponseEntity<Map> response = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, body, Map.class, token(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("INSUFFICIENT_STOCK");
        assertStock(enough, 5, 0);
        assertStock(shortId, 1, 0);
    }

    @Test
    void commit_leavesAvailableDown_andRevertRestoresIt() {
        UUID productId = UUID.randomUUID();
        seed(productId, 10);
        String token = token(UUID.randomUUID());
        ReservationResponse held = exchange(
                baseUrl() + "/reservations", HttpMethod.POST, reserveBody(UUID.randomUUID(), productId, 4),
                ReservationResponse.class, token).getBody();

        ResponseEntity<ReservationResponse> committed = exchange(
                baseUrl() + "/reservations/" + held.id() + "/commit",
                HttpMethod.POST, null, ReservationResponse.class, token);
        assertThat(committed.getBody().status()).isEqualTo(ReservationStatus.COMMITTED);
        assertStock(productId, 6, 0);

        ResponseEntity<ReservationResponse> reverted = exchange(
                baseUrl() + "/reservations/" + held.id() + "/revert",
                HttpMethod.POST, null, ReservationResponse.class, token);
        assertThat(reverted.getBody().status()).isEqualTo(ReservationStatus.REVERTED);
        assertStock(productId, 10, 0);

        exchange(baseUrl() + "/reservations/" + held.id() + "/revert",
                HttpMethod.POST, null, ReservationResponse.class, token);
        assertStock(productId, 10, 0);
    }

    private void seed(UUID productId, int available) {
        ResponseEntity<StockResponse> response = exchange(
                baseUrl() + "/stock/" + productId, HttpMethod.PUT, Map.of("available", available),
                StockResponse.class, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void assertStock(UUID productId, int available, int reserved) {
        Stock stock = stockRepository.findById(productId).orElseThrow();
        assertThat(stock.getAvailable()).isEqualTo(available);
        assertThat(stock.getReserved()).isEqualTo(reserved);
    }

    private Map<String, Object> reserveBody(UUID orderId, UUID productId, int quantity) {
        return Map.of(
                "orderId", orderId.toString(),
                "lines", java.util.List.of(Map.of("productId", productId.toString(), "quantity", quantity)));
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

    private <T> ResponseEntity<T> exchange(String url, HttpMethod method, Object body, Class<T> type, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
