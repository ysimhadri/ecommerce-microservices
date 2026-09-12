package com.ecommerce.portal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO pattern: response body for producer-API declaration and listing.
 */
public record ApiResponse(
        UUID id,
        UUID producerServiceId,
        String method,
        String pathPattern,
        List<String> requiredScopes,
        Instant createdAt
) {
}
