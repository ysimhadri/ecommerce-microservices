package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.CatalogProductSnapshot;
import com.ecommerce.order.exception.OrderExceptions.CartForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.InsufficientStockException;
import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.exception.OrderExceptions.PaymentUnavailableException;
import com.ecommerce.order.exception.OrderExceptions.ProductNotFoundException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** RestClient ports map downstream error codes without a database. */
class RemoteClientsTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void catalog_404_isProductNotFound_and200SnapshotsPrice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://catalog").build();
        RestCatalogClient catalog = new RestCatalogClient(client, objectMapper);
        UUID missing = UUID.randomUUID();
        UUID present = UUID.randomUUID();

        server.expect(requestTo("http://catalog/api/v1/catalog/products/" + missing))
                .andRespond(withStatus(NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"PRODUCT_NOT_FOUND\",\"message\":\"missing\"}"));
        server.expect(requestTo("http://catalog/api/v1/catalog/products/" + present))
                .andRespond(withStatus(OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"" + present + "\",\"name\":\"Headphones\",\"price\":9.99,"
                                + "\"description\":\"x\",\"categoryId\":\"" + UUID.randomUUID() + "\"}"));

        assertThatThrownBy(() -> catalog.getProduct(missing)).isInstanceOf(ProductNotFoundException.class);
        CatalogProductSnapshot product = catalog.getProduct(present);
        assertThat(product.name()).isEqualTo("Headphones");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("9.99"));
        server.verify();
    }

    @Test
    void inventory_409InsufficientStock_isMapped() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://inventory").build();
        RestInventoryClient inventory = new RestInventoryClient(client, objectMapper);
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        server.expect(requestTo("http://inventory/api/v1/inventory/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"short\"}"));

        assertThatThrownBy(() -> inventory.reserve(orderId, List.of(new ClientSnapshots.ReserveLine(productId, 1)), "token"))
                .isInstanceOf(InsufficientStockException.class);
        server.verify();
    }

    @Test
    void cart_403_isForbidden() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://cart").build();
        RestCartClient cart = new RestCartClient(client, objectMapper);
        UUID cartId = UUID.randomUUID();

        server.expect(requestTo("http://cart/api/v1/carts/" + cartId))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"CART_FORBIDDEN\",\"message\":\"nope\"}"));

        assertThatThrownBy(() -> cart.getCart(cartId, "token")).isInstanceOf(CartForbiddenException.class);
        server.verify();
    }

    @Test
    void cartCommands_postToLockUnlockClearAndRestore() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://cart").build();
        RestCartClient cart = new RestCartClient(client, objectMapper);
        UUID cartId = UUID.randomUUID();

        expectCartPost(server, cartId, "lock");
        expectCartPost(server, cartId, "unlock");
        expectCartPost(server, cartId, "clear");
        expectCartPost(server, cartId, "restore");

        cart.lock(cartId, "token");
        cart.unlock(cartId, "token");
        cart.clear(cartId, "token");
        cart.restore(cartId, "token");
        server.verify();
    }

    @Test
    void inventoryCommands_postToReleaseCommitAndRevert() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://inventory").build();
        RestInventoryClient inventory = new RestInventoryClient(client, objectMapper);
        UUID reservationId = UUID.randomUUID();

        expectInventoryPost(server, reservationId, "release");
        expectInventoryPost(server, reservationId, "commit");
        expectInventoryPost(server, reservationId, "revert");

        inventory.release(reservationId, "token");
        inventory.commit(reservationId, "token");
        inventory.revert(reservationId, "token");
        server.verify();
    }

    @Test
    void payment_forwardsSimulateDecline_andMapsDeclineAndOutage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://payment").build();
        RestPaymentClient payment = new RestPaymentClient(client, objectMapper);
        UUID orderId = UUID.randomUUID();

        server.expect(requestTo("http://payment/api/v1/payments/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(content().json("{\"orderId\":\"" + orderId + "\",\"currency\":\"USD\",\"simulateDecline\":true}"))
                .andRespond(withStatus(CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"PAYMENT_DECLINED\",\"message\":\"Payment was declined\"}"));
        server.expect(requestTo("http://payment/api/v1/payments/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"PAYMENT_UNAVAILABLE\",\"message\":\"down\"}"));
        server.expect(requestTo("http://payment/api/v1/payments/authorizations/" + orderId + "/void"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(OK));

        assertThatThrownBy(() -> payment.authorize(orderId, new BigDecimal("19.98"), "USD", true, "token"))
                .isInstanceOf(PaymentDeclinedException.class);
        assertThatThrownBy(() -> payment.authorize(orderId, new BigDecimal("19.98"), "USD", false, "token"))
                .isInstanceOf(PaymentUnavailableException.class);
        payment.voidAuthorization(orderId, "token");
        server.verify();
    }

    @Test
    void payment_connectionFailure_isUnavailable() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(500));
        requestFactory.setReadTimeout(Duration.ofMillis(500));
        RestClient client = RestClient.builder().requestFactory(requestFactory).baseUrl("http://127.0.0.1:1").build();
        RestPaymentClient payment = new RestPaymentClient(client, objectMapper);

        assertThatThrownBy(() -> payment.authorize(UUID.randomUUID(), new BigDecimal("1.00"), "USD", false, "token"))
                .isInstanceOf(PaymentUnavailableException.class);
    }

    private static void expectCartPost(MockRestServiceServer server, UUID cartId, String command) {
        server.expect(requestTo("http://cart/api/v1/carts/" + cartId + "/" + command))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(OK));
    }

    private static void expectInventoryPost(MockRestServiceServer server, UUID reservationId, String command) {
        server.expect(requestTo("http://inventory/api/v1/inventory/reservations/" + reservationId + "/" + command))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(OK));
    }
}
