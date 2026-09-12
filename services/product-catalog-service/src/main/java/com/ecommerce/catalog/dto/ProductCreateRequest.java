package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO pattern: request payload for {@code POST /api/v1/catalog/products}.
 * Kept separate from {@link com.ecommerce.catalog.model.Product} so the
 * caller can only ever send caller-shaped fields, never anything
 * persistence-shaped.
 */
public record ProductCreateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", message = "price must not be negative")
        BigDecimal price,

        @NotNull(message = "categoryId is required")
        UUID categoryId
) {
}
