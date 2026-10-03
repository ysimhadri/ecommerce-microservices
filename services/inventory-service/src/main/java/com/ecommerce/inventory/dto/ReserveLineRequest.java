package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReserveLineRequest(
        @NotNull(message = "must not be null") UUID productId,
        @NotNull(message = "must not be null") @Min(value = 1, message = "must be at least 1") Integer quantity
) {
}
