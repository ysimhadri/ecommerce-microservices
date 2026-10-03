package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.CartResponse;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartControllerIntegrationTest {

    static final UUID P1 = UUID.randomUUID();
    static final UUID P2 = UUID.randomUUID();
    static final UUID P_BROKEN = UUID.randomUUID();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static HttpServer catalogStub;

    static {
        try {
            catalogStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            catalogStub.createContext("/api/v1/catalog/products/", ex -> {
                String id = ex.getRequestURI().getPath().substring("/api/v1/catalog/products/".length());
                String body = null;
                if (id.equals(P1.toString())) body = "{\"id\":\"" + id + "\",\"name\":\"Alpha\",\"price\":10.00,\"categoryName\":\"x\"}";
                if (id.equals(P2.toString())) body = "{\"id\":\"" + id + "\",\"name\":\"Beta\",\"price\":5.50}";
                if (id.equals(P_BROKEN.toString())) {
                    ex.sendResponseHeaders(500, -1);
                    ex.close();
                    return;
                }
                byte[] bytes = (body != null ? body : "{\"code\":\"PRODUCT_NOT_FOUND\"}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(body != null ? 200 : 404, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            catalogStub.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.catalog.base-url", () -> "http://localhost:" + catalogStub.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        catalogStub.stop(0);
    }

    @Autowired
    TestRestTemplate rest;

    @Value("${app.jwt.secret}")
    String secret;

    @BeforeEach
    void apacheFactory() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    String token(UUID userId) {
        return Jwts.builder().subject(userId.toString()).claim("email", "u@example.com")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    <T> ResponseEntity<T> call(HttpMethod m, String path, UUID user, Object body, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        if (user != null) h.setBearerAuth(token(user));
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/cart" + path, m, new HttpEntity<>(body, h), type);
    }

    @Test
    void noOrBadToken_401() {
        ResponseEntity<Map> none = call(HttpMethod.POST, "", null, null, Map.class);
        assertThat(none.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(none.getBody()).containsEntry("code", "UNAUTHORIZED");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("garbage");
        assertThat(rest.exchange("/api/v1/cart", HttpMethod.POST, new HttpEntity<>(h), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    String rawToken(java.util.function.Consumer<io.jsonwebtoken.JwtBuilder> customizer, String key, long ttlSeconds) {
        var b = Jwts.builder().issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)));
        customizer.accept(b);
        return b.signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    void assertUnauthorized(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        ResponseEntity<Map> r = rest.exchange("/api/v1/cart", HttpMethod.POST, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).containsEntry("code", "UNAUTHORIZED");
    }

    @Test
    void invalidTokens_401() {
        String sub = UUID.randomUUID().toString();
        assertUnauthorized(rawToken(b -> b.subject(sub), "a-completely-different-signing-key-32-bytes!!", 600));
        assertUnauthorized(rawToken(b -> b.subject(sub), secret, -60));
        assertUnauthorized(rawToken(b -> b.subject("not-a-uuid"), secret, 600));
        assertUnauthorized(rawToken(b -> { }, secret, 600));
    }

    @Test
    void catalogFailure_500_cartUnchanged() {
        UUID u = UUID.randomUUID();
        ResponseEntity<Map> r = call(HttpMethod.POST, "/items", u, Map.of("productId", P_BROKEN, "quantity", 1), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(call(HttpMethod.POST, "", u, null, CartResponse.class).getBody().items()).isEmpty();
    }

    @Test
    void updateAndRemoveOnCheckedOutCart_409() {
        UUID u = UUID.randomUUID();
        CartResponse c = call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 1), CartResponse.class).getBody();
        UUID item = c.items().get(0).id();
        call(HttpMethod.POST, "/checkout", u, null, CartResponse.class);
        ResponseEntity<Map> upd = call(HttpMethod.PATCH, "/items/" + item, u, Map.of("quantity", 2), Map.class);
        assertThat(upd.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(upd.getBody()).containsEntry("code", "CART_NOT_ACTIVE");
        ResponseEntity<Map> del = call(HttpMethod.DELETE, "/items/" + item, u, null, Map.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(del.getBody()).containsEntry("code", "CART_NOT_ACTIVE");
    }

    @Test
    void getOrCreate_isIdempotent_andFreshAfterCheckout() {
        UUID u = UUID.randomUUID();
        CartResponse a = call(HttpMethod.POST, "", u, null, CartResponse.class).getBody();
        CartResponse b = call(HttpMethod.POST, "", u, null, CartResponse.class).getBody();
        assertThat(a.status().name()).isEqualTo("ACTIVE");
        assertThat(a.items()).isEmpty();
        assertThat(b.id()).isEqualTo(a.id());

        call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 1), CartResponse.class);
        assertThat(call(HttpMethod.POST, "/checkout", u, null, CartResponse.class).getBody().status().name())
                .isEqualTo("CHECKED_OUT");
        CartResponse c = call(HttpMethod.POST, "", u, null, CartResponse.class).getBody();
        assertThat(c.id()).isNotEqualTo(a.id());
        assertThat(c.items()).isEmpty();
    }

    @Test
    void fullFlow_addMergeUpdateRemoveClearCheckout() {
        UUID u = UUID.randomUUID();
        call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 2), CartResponse.class);
        CartResponse two = call(HttpMethod.POST, "/items", u, Map.of("productId", P2, "quantity", 1), CartResponse.class).getBody();
        assertThat(two.items()).hasSize(2);
        CartResponse merged = call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 3), CartResponse.class).getBody();
        assertThat(merged.items()).hasSize(2);
        assertThat(merged.items().stream().filter(i -> i.productId().equals(P1)).findFirst().get().quantity()).isEqualTo(5);
        assertThat(merged.total()).isEqualByComparingTo(new BigDecimal("55.50"));

        UUID p2Item = merged.items().stream().filter(i -> i.productId().equals(P2)).findFirst().get().id();
        CartResponse upd = call(HttpMethod.PATCH, "/items/" + p2Item, u, Map.of("quantity", 4), CartResponse.class).getBody();
        assertThat(upd.total()).isEqualByComparingTo(new BigDecimal("72.00"));
        assertThat(call(HttpMethod.PATCH, "/items/" + p2Item, u, Map.of("quantity", 0), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> missing = call(HttpMethod.PATCH, "/items/" + UUID.randomUUID(), u, Map.of("quantity", 1), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("code", "CART_ITEM_NOT_FOUND");

        assertThat(call(HttpMethod.DELETE, "/items/" + p2Item, u, null, CartResponse.class).getBody().items()).hasSize(1);
        CartResponse cleared = call(HttpMethod.DELETE, "", u, null, CartResponse.class).getBody();
        assertThat(cleared.items()).isEmpty();
        assertThat(cleared.status().name()).isEqualTo("ACTIVE");

        ResponseEntity<Map> empty = call(HttpMethod.POST, "/checkout", u, null, Map.class);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody()).containsEntry("code", "CART_EMPTY");

        call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 1), CartResponse.class);
        assertThat(call(HttpMethod.POST, "/checkout", u, null, CartResponse.class).getBody().status().name())
                .isEqualTo("CHECKED_OUT");

        ResponseEntity<Map> blocked = call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 1), Map.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody()).containsEntry("code", "CART_NOT_ACTIVE");
        assertThat(call(HttpMethod.DELETE, "", u, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addItem_invalidInputs() {
        UUID u = UUID.randomUUID();
        ResponseEntity<Map> badQty = call(HttpMethod.POST, "/items", u, Map.of("productId", P1, "quantity", 0), Map.class);
        assertThat(badQty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badQty.getBody()).containsEntry("code", "VALIDATION_ERROR");

        ResponseEntity<Map> unknown = call(HttpMethod.POST, "/items", u, Map.of("productId", UUID.randomUUID(), "quantity", 1), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody()).containsEntry("code", "INVALID_PRODUCT_ID");
        assertThat(call(HttpMethod.POST, "", u, null, CartResponse.class).getBody().items()).isEmpty();
    }

    @Test
    void usersCannotSeeEachOthersItems() {
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID();
        CartResponse c = call(HttpMethod.POST, "/items", u1, Map.of("productId", P1, "quantity", 1), CartResponse.class).getBody();
        UUID item = c.items().get(0).id();
        assertThat(call(HttpMethod.DELETE, "/items/" + item, u2, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
