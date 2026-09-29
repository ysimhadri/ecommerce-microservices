package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.repository.CartRepository;
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
import org.springframework.transaction.annotation.Transactional;
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
 * Testcontainers-backed integration test exercising the cart HTTP API
 * against a real PostgreSQL instance (Flyway migrations run for real),
 * including clear/restore keeping the stored lines.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CartRepository cartRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void useApacheHttpClientRequestFactory() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/carts";
    }

    @Test
    void create_withValidUserJwt_returns201EmptyActiveCart() {
        UUID ownerId = UUID.randomUUID();

        ResponseEntity<CartResponse> response = post(baseUrl(), null, CartResponse.class, token(ownerId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ownerId()).isEqualTo(ownerId);
        assertThat(response.getBody().status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(response.getBody().lines()).isEmpty();
    }

    @Test
    void addItem_mergesSameProduct_andRejectsNonPositiveQuantityAndInactiveCart() {
        UUID ownerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CartResponse created = post(baseUrl(), null, CartResponse.class, token(ownerId)).getBody();

        ResponseEntity<CartResponse> added = post(
                baseUrl() + "/" + created.id() + "/items",
                new AddItemRequest(productId, 1),
                CartResponse.class,
                token(ownerId));
        ResponseEntity<CartResponse> merged = post(
                baseUrl() + "/" + created.id() + "/items",
                new AddItemRequest(productId, 3),
                CartResponse.class,
                token(ownerId));

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merged.getBody().lines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(productId);
            assertThat(line.quantity()).isEqualTo(4);
        });

        ResponseEntity<Map> invalid = post(
                baseUrl() + "/" + created.id() + "/items",
                new AddItemRequest(productId, 0),
                Map.class,
                token(ownerId));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        post(baseUrl() + "/" + created.id() + "/lock", null, CartResponse.class, token(ownerId));
        ResponseEntity<Map> inactive = post(
                baseUrl() + "/" + created.id() + "/items",
                new AddItemRequest(UUID.randomUUID(), 1),
                Map.class,
                token(ownerId));
        assertThat(inactive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inactive.getBody().get("code")).isEqualTo("CART_NOT_ACTIVE");
    }

    @Test
    void get_ownerSucceeds_missingIs404_otherUserIs403_missingTokenIs401() {
        UUID ownerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CartResponse created = post(baseUrl(), null, CartResponse.class, token(ownerId)).getBody();
        post(baseUrl() + "/" + created.id() + "/items", new AddItemRequest(productId, 2), CartResponse.class, token(ownerId));

        ResponseEntity<CartResponse> owner = exchange(
                baseUrl() + "/" + created.id(), HttpMethod.GET, null, CartResponse.class, token(ownerId));
        assertThat(owner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.getBody().lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(2));

        ResponseEntity<Map> missing = exchange(
                baseUrl() + "/" + UUID.randomUUID(), HttpMethod.GET, null, Map.class, token(ownerId));
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().get("code")).isEqualTo("CART_NOT_FOUND");

        ResponseEntity<Map> foreign = exchange(
                baseUrl() + "/" + created.id(), HttpMethod.GET, null, Map.class, token(UUID.randomUUID()));
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(foreign.getBody().get("code")).isEqualTo("CART_FORBIDDEN");

        ResponseEntity<Map> anonymous = exchange(
                baseUrl() + "/" + created.id(), HttpMethod.GET, null, Map.class, null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @Transactional
    void clear_hidesLinesUntilRestore_andRowsRemainStored() {
        UUID ownerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String token = token(ownerId);
        CartResponse created = post(baseUrl(), null, CartResponse.class, token).getBody();
        post(baseUrl() + "/" + created.id() + "/items", new AddItemRequest(productId, 2), CartResponse.class, token);
        post(baseUrl() + "/" + created.id() + "/lock", null, CartResponse.class, token);

        ResponseEntity<CartResponse> cleared = post(
                baseUrl() + "/" + created.id() + "/clear", null, CartResponse.class, token);

        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().status()).isEqualTo(CartStatus.CHECKED_OUT);
        assertThat(cleared.getBody().lines()).isEmpty();
        assertThat(cartRepository.findById(created.id()).orElseThrow().getLines())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.getProductId()).isEqualTo(productId);
                    assertThat(line.getQuantity()).isEqualTo(2);
                });

        ResponseEntity<CartResponse> restored = post(
                baseUrl() + "/" + created.id() + "/restore", null, CartResponse.class, token);
        assertThat(restored.getBody().status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(restored.getBody().lines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(productId);
            assertThat(line.quantity()).isEqualTo(2);
        });
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

    private <T> ResponseEntity<T> post(String url, Object body, Class<T> responseType, String bearerToken) {
        return exchange(url, HttpMethod.POST, body, responseType, bearerToken);
    }

    private <T> ResponseEntity<T> exchange(String url, HttpMethod method, Object body, Class<T> responseType, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), responseType);
    }
}
