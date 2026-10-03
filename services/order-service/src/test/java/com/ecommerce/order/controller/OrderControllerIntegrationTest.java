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
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentAuthorizationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Place-order HTTP against a real order database. Cart, inventory, and catalog
 * ports are mocked so the compensation path does not need the other processes.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAuthorizationRepository paymentAuthorizationRepository;

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
        paymentAuthorizationRepository.deleteAll();
        orderRepository.deleteAll();
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

        ResponseEntity<OrderResponse> response = post(userId, Map.of("cartId", cartId.toString()), OrderResponse.class);

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
        assertThat(paymentAuthorizationRepository.findByOrderId(body.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);
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

        ResponseEntity<Map> response = post(userId, Map.of(
                "cartId", cartId.toString(),
                "simulatePaymentFailure", true), Map.class);

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
        assertThat(paymentAuthorizationRepository.findByOrderId(orderId)).isEmpty();
        verify(inventoryClient).release(eq(reservationId), anyString());
        verify(cartClient).unlock(eq(cartId), anyString());
        verify(inventoryClient, never()).commit(any(), anyString());
        verify(cartClient, never()).clear(any(), anyString());
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
