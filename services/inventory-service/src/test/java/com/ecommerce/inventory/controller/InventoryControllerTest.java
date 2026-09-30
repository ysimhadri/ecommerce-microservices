package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.HoldOutcome;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.exception.GlobalExceptionHandler;
import com.ecommerce.inventory.exception.InventoryExceptions.InsufficientStockException;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.security.SecurityConfig;
import com.ecommerce.inventory.security.UserJwtFilter;
import com.ecommerce.inventory.security.UserJwtValidator;
import com.ecommerce.inventory.service.InventoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract for stock seeding and reservation auth, without a database. */
@WebMvcTest(controllers = InventoryController.class)
@Import({SecurityConfig.class, UserJwtFilter.class, UserJwtValidator.class, GlobalExceptionHandler.class})
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void upsert_withoutToken_returns200() throws Exception {
        UUID productId = UUID.randomUUID();
        when(inventoryService.upsert(productId, 5)).thenReturn(new StockResponse(productId, 5, 0));

        mockMvc.perform(put("/api/v1/inventory/stock/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(5))
                .andExpect(jsonPath("$.reserved").value(0));
    }

    @Test
    void upsert_negativeAvailable_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/stock/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reserve_withoutToken_returns401() throws Exception {
        String body = "{\"orderId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"productId\":\""
                + UUID.randomUUID() + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reserve_whenStockIsShort_returns409InsufficientStock() throws Exception {
        when(inventoryService.hold(any(), any())).thenThrow(
                new InsufficientStockException("Insufficient stock to fill every line"));
        String body = "{\"orderId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"productId\":\""
                + UUID.randomUUID() + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void getStock_returnsAvailable() throws Exception {
        UUID productId = UUID.randomUUID();
        when(inventoryService.get(productId)).thenReturn(new StockResponse(productId, 8, 2));

        mockMvc.perform(get("/api/v1/inventory/stock/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(8))
                .andExpect(jsonPath("$.reserved").value(2));
    }

    @Test
    void release_requiresTokenAndReturnsReleased() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(inventoryService.release(eq(reservationId))).thenReturn(
                new ReservationResponse(reservationId, orderId, ReservationStatus.RELEASED, List.of()));

        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/release")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
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
