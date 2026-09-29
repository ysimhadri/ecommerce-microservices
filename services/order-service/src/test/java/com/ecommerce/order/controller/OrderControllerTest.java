package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderLineResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.GlobalExceptionHandler;
import com.ecommerce.order.exception.OrderExceptions.CartEmptyException;
import com.ecommerce.order.exception.OrderExceptions.CartForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.CompensatedOrderException;
import com.ecommerce.order.exception.OrderExceptions.OrderForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.OrderNotFoundException;
import com.ecommerce.order.exception.OrderExceptions.ProductNotFoundException;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.saga.OrderSagaOrchestrator;
import com.ecommerce.order.security.SecurityConfig;
import com.ecommerce.order.security.UserJwtFilter;
import com.ecommerce.order.security.UserJwtValidator;
import com.ecommerce.order.service.OrderQueryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP edge for place and get: 201, 409 placement body, and ordinary ErrorResponse codes. */
@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, UserJwtFilter.class, UserJwtValidator.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderSagaOrchestrator orderSagaOrchestrator;

    @MockBean
    private OrderQueryService orderQueryService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void place_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void place_happyPath_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderSagaOrchestrator.place(eq(userId), any(), eq(cartId), eq(false)))
                .thenReturn(order(orderId, cartId, OrderStatus.CONFIRMED, null));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.cartId").value(cartId.toString()))
                .andExpect(jsonPath("$.total").value(19.98))
                .andExpect(jsonPath("$.lines[0].productName").value("Headphones"));
    }

    @Test
    void place_paymentDeclined_returns409WithOrderIdAndNoTimestamp() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderSagaOrchestrator.place(eq(userId), any(), eq(cartId), eq(true)))
                .thenThrow(new CompensatedOrderException(orderId, "PAYMENT_DECLINED", "Payment was declined"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"simulatePaymentFailure\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.message").value("Payment was declined"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void place_insufficientStock_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderSagaOrchestrator.place(eq(userId), any(), eq(cartId), anyBoolean()))
                .thenThrow(new CompensatedOrderException(orderId, "INSUFFICIENT_STOCK", "Insufficient stock to fill every line"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void place_unknownProduct_returns400AndOrdinaryErrorBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(orderSagaOrchestrator.place(eq(userId), any(), eq(cartId), anyBoolean()))
                .thenThrow(new ProductNotFoundException("Product not found"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void place_emptyCart_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(orderSagaOrchestrator.place(eq(userId), any(), eq(cartId), anyBoolean()))
                .thenThrow(new CartEmptyException("Cart has no lines"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void place_otherUsersCart_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(orderSagaOrchestrator.place(eq(userId), any(), eq(cartId), anyBoolean()))
                .thenThrow(new CartForbiddenException("Cart belongs to another user"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_FORBIDDEN"));
    }

    @Test
    void get_ownerSeesOrder_otherUserIs403_missingIs404() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(orderQueryService.get(orderId, ownerId)).thenReturn(order(orderId, cartId, OrderStatus.CONFIRMED, null));
        when(orderQueryService.get(orderId, UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenThrow(new OrderForbiddenException("Order belongs to another user"));

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + token(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + token(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"));

        UUID missing = UUID.randomUUID();
        when(orderQueryService.get(eq(missing), eq(ownerId))).thenThrow(new OrderNotFoundException("Order not found"));
        mockMvc.perform(get("/api/v1/orders/" + missing).header("Authorization", "Bearer " + token(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private OrderResponse order(UUID orderId, UUID cartId, OrderStatus status, String failureCode) {
        return new OrderResponse(
                orderId,
                status,
                cartId,
                new BigDecimal("19.98"),
                List.of(new OrderLineResponse(UUID.randomUUID(), "Headphones", new BigDecimal("9.99"), 2, new BigDecimal("19.98"))),
                failureCode);
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
