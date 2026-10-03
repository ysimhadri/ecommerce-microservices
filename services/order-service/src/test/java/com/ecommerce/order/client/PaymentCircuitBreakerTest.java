package com.ecommerce.order.client;

import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.exception.OrderExceptions.PaymentUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repeated payment 5xx opens the {@code payment} breaker. The next call fails
 * as {@code PAYMENT_UNAVAILABLE} without reaching the stub. Declines do not open it.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.payment.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.payment.minimumNumberOfCalls=4",
        "resilience4j.circuitbreaker.instances.payment.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.payment.waitDurationInOpenState=30s"
})
class PaymentCircuitBreakerTest {

    private static final PaymentFailureStub PAYMENT = start();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void paymentUrl(DynamicPropertyRegistry registry) {
        registry.add("app.clients.payment-base-url", PAYMENT::baseUrl);
    }

    @Autowired
    private RestPaymentClient restPaymentClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetBreaker() {
        circuitBreakerRegistry.circuitBreaker("payment").reset();
        PAYMENT.reset();
    }

    @AfterAll
    static void stopStub() {
        PAYMENT.stop();
    }

    @Test
    void repeated5xx_opensBreaker_andNextCallDoesNotReachPayment() {
        PAYMENT.mode(PaymentFailureStub.Mode.UNAVAILABLE);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(this::authorize)
                    .isInstanceOf(PaymentUnavailableException.class);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("payment").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int hits = PAYMENT.hits();

        assertThatThrownBy(this::authorize).isInstanceOf(PaymentUnavailableException.class);

        assertThat(PAYMENT.hits()).isEqualTo(hits);
    }

    @Test
    void declines_doNotOpenTheBreaker() {
        PAYMENT.mode(PaymentFailureStub.Mode.DECLINE);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(this::authorize).isInstanceOf(PaymentDeclinedException.class);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("payment").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(PAYMENT.hits()).isEqualTo(6);
    }

    private void authorize() {
        restPaymentClient.authorize(UUID.randomUUID(), new BigDecimal("10.00"), "USD", false, "token");
    }

    private static PaymentFailureStub start() {
        try {
            return new PaymentFailureStub();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start payment stub", ex);
        }
    }

    static final class PaymentFailureStub {
        enum Mode { UNAVAILABLE, DECLINE }

        private final HttpServer server;
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.UNAVAILABLE);
        private final AtomicInteger hits = new AtomicInteger();

        PaymentFailureStub() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/payments/authorizations", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void mode(Mode next) {
            mode.set(next);
        }

        void reset() {
            hits.set(0);
        }

        int hits() {
            return hits.get();
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                hits.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                if (mode.get() == Mode.DECLINE) {
                    write(exchange, 409, "{\"code\":\"PAYMENT_DECLINED\",\"message\":\"Payment was declined\"}");
                    return;
                }
                write(exchange, 503, "{\"code\":\"PAYMENT_UNAVAILABLE\",\"message\":\"Payment service is unavailable\"}");
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
}
