package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO pattern: request payload for {@code POST /api/v1/catalog/categories}.
 */
public record CategoryCreateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
