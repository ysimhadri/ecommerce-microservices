package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.CartLineResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.exception.CartForbiddenException;
import com.ecommerce.cart.exception.CartNotActiveException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.GlobalExceptionHandler;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.security.SecurityConfig;
import com.ecommerce.cart.security.UserJwtFilter;
import com.ecommerce.cart.security.UserJwtValidator;
import com.ecommerce.cart.service.CartService;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract for the cart matrix rows that do not need a database:
 * 201 create, 200 get, 401, 403, 404, 400 validation, 409 not active.
 */
@WebMvcTest(controllers = CartController.class)
@Import({SecurityConfig.class, UserJwtFilter.class, UserJwtValidator.class, GlobalExceptionHandler.class})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void create_withValidUserJwt_returns201EmptyActiveCart() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(cartService.create(ownerId)).thenReturn(cart(cartId, ownerId, CartStatus.ACTIVE, List.of()));

        mockMvc.perform(post("/api/v1/carts").header("Authorization", "Bearer " + token(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cartId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/carts/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void get_whenOwner_returns200() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartService.get(ownerId, cartId)).thenReturn(
                cart(cartId, ownerId, CartStatus.ACTIVE, List.of(new CartLineResponse(productId, 2))));

        mockMvc.perform(get("/api/v1/carts/" + cartId).header("Authorization", "Bearer " + token(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lines[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    @Test
    void get_whenMissing_returns404() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(cartService.get(ownerId, cartId)).thenThrow(new CartNotFoundException("Cart not found"));

        mockMvc.perform(get("/api/v1/carts/" + cartId).header("Authorization", "Bearer " + token(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_NOT_FOUND"));
    }

    @Test
    void get_whenOwnedBySomeoneElse_returns403() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(cartService.get(eq(ownerId), eq(cartId)))
                .thenThrow(new CartForbiddenException("Cart belongs to another user"));

        mockMvc.perform(get("/api/v1/carts/" + cartId).header("Authorization", "Bearer " + token(ownerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_FORBIDDEN"));
    }

    @Test
    void addItem_withQuantityBelowOne_returns400ValidationError() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String body = "{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}";

        mockMvc.perform(post("/api/v1/carts/" + UUID.randomUUID() + "/items")
                        .header("Authorization", "Bearer " + token(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addItem_whenCartIsNotActive_returns409() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartService.addItem(eq(ownerId), eq(cartId), eq(productId), eq(1)))
                .thenThrow(new CartNotActiveException("Cart is not active"));
        String body = "{\"productId\":\"" + productId + "\",\"quantity\":1}";

        mockMvc.perform(post("/api/v1/carts/" + cartId + "/items")
                        .header("Authorization", "Bearer " + token(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_NOT_ACTIVE"));
    }

    private CartResponse cart(UUID id, UUID ownerId, CartStatus status, List<CartLineResponse> lines) {
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        return new CartResponse(id, ownerId, status, lines, now, now);
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
