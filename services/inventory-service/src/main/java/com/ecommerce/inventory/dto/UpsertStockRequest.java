package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** DTO pattern: absolute available count for a stock upsert. Negative is rejected. */
public record UpsertStockRequest(
        @NotNull(message = "must not be null") @Min(value = 0, message = "must not be negative") Integer available
) {
}
