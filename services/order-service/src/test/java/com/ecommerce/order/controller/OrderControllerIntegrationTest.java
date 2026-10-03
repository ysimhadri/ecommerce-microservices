package com.ecommerce.order.controller;

import com.ecommerce.order.client.CartClient;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.ClientSnapshots.CartLineSnapshot;
import com.ecommerce.order.client.ClientSnapshots.CartSnapshot;
import com.ecommerce.order.client.ClientSnapshots.CatalogProductSnapshot;
import com.ecommerce.order.client.ClientSnapshots.ReservationSnapshot;
import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderExceptions.CartForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.InsufficientStockException;
import com.ecommerce.order.exception.OrderExceptions.ProductNotFoundException;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Place-order HTTP against a real order database. Cart, inventory, and catalog
 * ports are mocked. Payment is a local HTTP stub reached through {@code PAYMENT_SERVICE_URL}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIntegrationTest {

    private static final PaymentStub PAYMENT = startPaymentStub();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void paymentUrl(DynamicPropertyRegistry registry) {
        registry.add("app.clients.payment-base-url", PAYMENT::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private CartClient cartClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private CatalogClient catalogClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void resetDatabase() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        orderRepository.deleteAll();
        PAYMENT.reset();
    }

    @Test
    @Transactional
    void place_happyPath_returns201Confirmed_andPersistsTheOrder() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        stubCart(cartId, userId, productId);
        when(catalogClient.getProduct(productId)).thenReturn(new CatalogProductSnapshot(productId, "Headphones", new BigDecimal("9.99")));
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenReturn(new ReservationSnapshot(reservationId, UUID.randomUUID(), "HELD"));
        String jwt = token(userId);

        ResponseEntity<OrderResponse> response = exchange(
                "/api/v1/orders", HttpMethod.POST, Map.of("cartId", cartId.toString()), OrderResponse.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(body.cartId()).isEqualTo(cartId);
        assertThat(body.total()).isEqualByComparingTo("19.98");
        assertThat(body.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo("Headphones");
            assertThat(line.quantity()).isEqualTo(2);
        });
        CustomerOrder stored = orderRepository.findById(body.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(stored.getLines()).hasSize(1);
        assertThat(PAYMENT.storedOrderIds()).containsExactly(body.orderId().toString());
        assertThat(PAYMENT.voids()).isZero();
        assertThat(PAYMENT.lastAuthorize().path("simulateDecline").asBoolean()).isFalse();
        assertThat(PAYMENT.lastAuthorize().path("currency").asText()).isEqualTo("USD");
        assertThat(PAYMENT.lastAuthorize().path("amount").decimalValue()).isEqualByComparingTo("19.98");
        assertThat(PAYMENT.lastAuthorization()).isEqualTo("Bearer " + jwt);
        verify(cartClient).lock(eq(cartId), anyString());
        verify(inventoryClient).commit(eq(reservationId), anyString());
        verify(cartClient).clear(eq(cartId), anyString());
        verify(inventoryClient, never()).release(any(), anyString());

        ResponseEntity<OrderResponse> fetched = exchange(
                "/api/v1/orders/" + body.orderId(), HttpMethod.GET, null, OrderResponse.class, token(userId));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);

        ResponseEntity<Map> foreign = exchange(
                "/api/v1/orders/" + body.orderId(), HttpMethod.GET, null, Map.class, token(UUID.randomUUID()));
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(foreign.getBody().get("code")).isEqualTo("ORDER_FORBIDDEN");
    }

    @Test
    @Transactional
    void place_paymentDeclined_returns409Cancelled_andCompensates() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        stubCart(cartId, userId, productId);
        when(catalogClient.getProduct(productId)).thenReturn(new CatalogProductSnapshot(productId, "Headphones", new BigDecimal("9.99")));
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenReturn(new ReservationSnapshot(reservationId, UUID.randomUUID(), "HELD"));
        String jwt = token(userId);

        ResponseEntity<Map> response = exchange("/api/v1/orders", HttpMethod.POST, Map.of(
                "cartId", cartId.toString(),
                "simulatePaymentFailure", true), Map.class, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_DECLINED");
        assertThat(response.getBody()).containsKeys("orderId", "message");
        assertThat(response.getBody()).doesNotContainKey("timestamp");
        UUID orderId = UUID.fromString(response.getBody().get("orderId").toString());
        CustomerOrder stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stored.getFailureCode()).isEqualTo("PAYMENT_DECLINED");
        assertThat(stored.getLines()).isNotEmpty();
        assertThat(PAYMENT.storedOrderIds()).isEmpty();
        assertThat(PAYMENT.voids()).isZero();
        assertThat(PAYMENT.lastAuthorize().path("simulateDecline").asBoolean()).isTrue();
        assertThat(PAYMENT.lastAuthorize().path("amount").decimalValue()).isEqualByComparingTo("19.98");
        assertThat(PAYMENT.lastAuthorization()).isEqualTo("Bearer " + jwt);
        verify(inventoryClient).release(eq(reservationId), anyString());
        verify(cartClient).unlock(eq(cartId), anyString());
        verify(inventoryClient, never()).commit(any(), anyString());
        verify(cartClient, never()).clear(any(), anyString());
    }

    @Test
    void place_paymentOutage_returns409Cancelled_andCompensatesWithoutVoid() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        stubCart(cartId, userId, productId);
        when(catalogClient.getProduct(productId)).thenReturn(new CatalogProductSnapshot(productId, "Headphones", new BigDecimal("9.99")));
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenReturn(new ReservationSnapshot(reservationId, UUID.randomUUID(), "HELD"));
        PAYMENT.mode(PaymentStub.Mode.UNAVAILABLE);

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_UNAVAILABLE");
        assertThat(response.getBody().get("message")).isEqualTo("Payment service is unavailable");
        UUID orderId = UUID.fromString(response.getBody().get("orderId").toString());
        CustomerOrder stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stored.getFailureCode()).isEqualTo("PAYMENT_UNAVAILABLE");
        assertThat(PAYMENT.storedOrderIds()).isEmpty();
        assertThat(PAYMENT.voids()).isZero();
        verify(inventoryClient).release(eq(reservationId), anyString());
        verify(cartClient).unlock(eq(cartId), anyString());
        verify(inventoryClient, never()).commit(any(), anyString());
        verify(cartClient, never()).clear(any(), anyString());
    }

    @Test
    void place_failureAfterAuthorize_voidsAndCancelsWithSagaFailed() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        stubCart(cartId, userId, productId);
        when(catalogClient.getProduct(productId)).thenReturn(new CatalogProductSnapshot(productId, "Headphones", new BigDecimal("9.99")));
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenReturn(new ReservationSnapshot(reservationId, UUID.randomUUID(), "HELD"));
        doThrow(new IllegalStateException("commit failed")).when(inventoryClient).commit(eq(reservationId), anyString());

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(response.getBody().get("code")).isEqualTo("SAGA_FAILED");
        UUID orderId = UUID.fromString(response.getBody().get("orderId").toString());
        CustomerOrder stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stored.getFailureCode()).isEqualTo("SAGA_FAILED");
        assertThat(PAYMENT.voids()).isEqualTo(1);
    }

    @Test
    void place_insufficientStock_unlocksAndCancels() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        stubCart(cartId, userId, productId);
        when(catalogClient.getProduct(productId)).thenReturn(new CatalogProductSnapshot(productId, "Headphones", new BigDecimal("9.99")));
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InsufficientStockException("Insufficient stock to fill every line"));

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        UUID orderId = UUID.fromString(response.getBody().get("orderId").toString());
        assertThat(orderRepository.findById(orderId).orElseThrow().getFailureCode()).isEqualTo("INSUFFICIENT_STOCK");
        verify(cartClient).unlock(eq(cartId), anyString());
        verify(inventoryClient, never()).release(any(), anyString());
        verify(inventoryClient, never()).commit(any(), anyString());
    }

    @Test
    void place_unknownProduct_returns400AndWritesNoOrder() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        stubCart(cartId, userId, productId);
        when(catalogClient.getProduct(productId)).thenThrow(new ProductNotFoundException("Product not found"));

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(orderRepository.count()).isZero();
        verify(cartClient, never()).lock(any(), anyString());
    }

    @Test
    void place_emptyCart_returns400AndWritesNoOrder() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(cartClient.getCart(eq(cartId), anyString()))
                .thenReturn(new CartSnapshot(cartId, userId, "ACTIVE", List.of()));

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("CART_EMPTY");
        assertThat(orderRepository.count()).isZero();
        verify(catalogClient, never()).getProduct(any());
        verify(cartClient, never()).lock(any(), anyString());
    }

    @Test
    void place_whenCartIsNotActive_returns409AndWritesNoOrder() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartClient.getCart(eq(cartId), anyString())).thenReturn(
                new CartSnapshot(cartId, userId, "LOCKED", List.of(new CartLineSnapshot(productId, 1))));

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CART_NOT_ACTIVE");
        assertThat(orderRepository.count()).isZero();
        verify(cartClient, never()).lock(any(), anyString());
    }

    @Test
    void place_otherUsersCart_returns403AndWritesNoOrder() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(cartClient.getCart(eq(cartId), anyString()))
                .thenThrow(new CartForbiddenException("Cart belongs to another user"));

        ResponseEntity<Map> response = post(userId, Map.of("cartId", cartId.toString()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CART_FORBIDDEN");
        assertThat(orderRepository.count()).isZero();
        verify(cartClient, never()).lock(any(), anyString());
        verify(catalogClient, never()).getProduct(any());
    }

    private void stubCart(UUID cartId, UUID userId, UUID productId) {
        when(cartClient.getCart(eq(cartId), anyString())).thenReturn(
                new CartSnapshot(cartId, userId, "ACTIVE", List.of(new CartLineSnapshot(productId, 2))));
    }

    private <T> ResponseEntity<T> post(UUID userId, Object body, Class<T> type) {
        return exchange("/api/v1/orders", HttpMethod.POST, body, type, token(userId));
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, Class<T> type, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), type);
    }

    private static PaymentStub startPaymentStub() {
        try {
            return new PaymentStub();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start payment stub", ex);
        }
    }

    /**
     * Stand-in for payment-service. {@link Mode#UNAVAILABLE} answers 503 and stores nothing.
     * A body with {@code simulateDecline: true} answers 409 and stores nothing.
     */
    static final class PaymentStub {
        enum Mode { NORMAL, UNAVAILABLE }

        private final HttpServer server;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.NORMAL);
        private final AtomicInteger voids = new AtomicInteger();
        private final CopyOnWriteArrayList<String> storedOrderIds = new CopyOnWriteArrayList<>();
        private final AtomicReference<JsonNode> lastAuthorize = new AtomicReference<>();
        private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

        PaymentStub() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/payments/authorizations", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            mode.set(Mode.NORMAL);
            voids.set(0);
            storedOrderIds.clear();
            lastAuthorize.set(null);
            lastAuthorization.set(null);
        }

        void mode(Mode next) {
            mode.set(next);
        }

        int voids() {
            return voids.get();
        }

        List<String> storedOrderIds() {
            return List.copyOf(storedOrderIds);
        }

        JsonNode lastAuthorize() {
            return lastAuthorize.get();
        }

        String lastAuthorization() {
            return lastAuthorization.get();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String path = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/void")) {
                    voids.incrementAndGet();
                    write(exchange, 200, "");
                    return;
                }
                JsonNode body = objectMapper.readTree(exchange.getRequestBody());
                lastAuthorize.set(body);
                if (mode.get() == Mode.UNAVAILABLE) {
                    write(exchange, 503, "{\"code\":\"PAYMENT_UNAVAILABLE\",\"message\":\"Payment service is unavailable\"}");
                    return;
                }
                if (body.path("simulateDecline").asBoolean(false)) {
                    write(exchange, 409, "{\"code\":\"PAYMENT_DECLINED\",\"message\":\"Payment was declined\"}");
                    return;
                }
                String orderId = body.path("orderId").asText();
                storedOrderIds.add(orderId);
                write(exchange, 201, "{\"id\":\"" + UUID.randomUUID() + "\",\"orderId\":\"" + orderId
                        + "\",\"amount\":1,\"currency\":\"USD\",\"status\":\"AUTHORIZED\"}");
            } finally {
                exchange.close();
            }
        }

        private static void write(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
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
