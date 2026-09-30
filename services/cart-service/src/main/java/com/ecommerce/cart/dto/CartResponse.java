package com.ecommerce.cart.dto;

import com.ecommerce.cart.model.CartStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO pattern: response body for every cart route. A {@code CHECKED_OUT} cart
 * returns an empty {@code lines} list — the rows are still stored and come
 * back on restore.
 */
public record CartResponse(
        UUID id,
        UUID ownerId,
        CartStatus status,
        List<CartLineResponse> lines,
        Instant createdAt,
        Instant updatedAt
) {
}
