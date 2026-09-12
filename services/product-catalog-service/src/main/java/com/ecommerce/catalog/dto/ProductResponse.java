package com.ecommerce.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pattern: response body for product creation, lookup, listing and
 * search. {@code categoryName} is denormalized in here for the reader's
 * convenience - the client never needs a second round trip just to show a
 * product's category.
 */
public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        UUID categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
