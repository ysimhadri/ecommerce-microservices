package com.ecommerce.catalog.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pattern: response body for category creation and listing.
 */
public record CategoryResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {
}
