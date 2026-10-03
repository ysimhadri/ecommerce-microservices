package com.ecommerce.order.client;

import com.ecommerce.order.exception.OrderExceptions.PaymentUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The {@code paymentRestClient} bean's read timeout is 3s. A peer that accepts
 * and never writes must surface {@link PaymentUnavailableException} instead of blocking.
 */
@SpringBootTest(classes = PaymentRestClientTimeoutTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRestClientTimeoutTest {

    private static final SilentSocket SILENT = start();

    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry registry) {
        registry.add("app.clients.payment-base-url", SILENT::baseUrl);
        registry.add("app.clients.cart-base-url", () -> "http://127.0.0.1:9");
        registry.add("app.clients.inventory-base-url", () -> "http://127.0.0.1:9");
        registry.add("app.clients.catalog-base-url", () -> "http://127.0.0.1:9");
    }

    @Autowired
    @Qualifier("paymentRestClient")
    private org.springframework.web.client.RestClient paymentRestClient;

    @AfterAll
    static void stop() {
        SILENT.close();
    }

    @Test
    void paymentRestClient_readTimeout_isUnavailable() {
        RestPaymentClient client = new RestPaymentClient(paymentRestClient, new ObjectMapper());
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> client.authorize(UUID.randomUUID(), new BigDecimal("19.98"), "USD", false, "token"))
                        .isInstanceOf(PaymentUnavailableException.class));
    }

    private static SilentSocket start() {
        try {
            return new SilentSocket();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Configuration
    @Import(ClientConfig.class)
    @ImportAutoConfiguration({RestClientAutoConfiguration.class, JacksonAutoConfiguration.class})
    static class Config {
    }

    /** Accepts TCP connections and writes nothing, so the client blocks until its read timeout. */
    static final class SilentSocket {
        private final ServerSocket server;
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();
        private final Thread thread;

        SilentSocket() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            thread = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        accepted.add(server.accept());
                    } catch (IOException ex) {
                        break;
                    }
                }
            }, "silent-payment");
            thread.setDaemon(true);
            thread.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void close() {
            try {
                server.close();
            } catch (IOException ignored) {
                // shutting down
            }
            for (Socket socket : accepted) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // shutting down
                }
            }
        }
    }
}
