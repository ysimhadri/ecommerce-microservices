package com.ecommerce.portal.dto;

import com.ecommerce.portal.model.ServiceRole;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pattern: response body for {@code POST /api/v1/portal/services} only.
 * Carries the plaintext {@code clientSecret} - the one and only time it is
 * ever returned; only its BCrypt hash is persisted (see
 * {@code RegisteredService}). The caller must store it now.
 */
public record ServiceCreatedResponse(
        UUID id,
        String name,
        String displayName,
        String baseUrl,
        ServiceRole role,
        String clientId,
        String clientSecret,
        boolean active,
        Instant createdAt
) {
}
