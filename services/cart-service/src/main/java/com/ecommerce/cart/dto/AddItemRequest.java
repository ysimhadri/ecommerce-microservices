package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** DTO pattern: body for adding a catalog product to an active cart. */
public record AddItemRequest(
        @NotNull(message = "must not be null") UUID productId,
        @NotNull(message = "must not be null") @Min(value = 1, message = "must be at least 1") Integer quantity
) {
}
