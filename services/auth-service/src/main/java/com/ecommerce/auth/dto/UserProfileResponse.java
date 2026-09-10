package com.ecommerce.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pattern: response body for registration and {@code GET /me}.
 * Deliberately excludes {@code passwordHash} - only id, email, createdAt
 * ever cross the API boundary.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        Instant createdAt
) {
}
