package com.ecommerce.portal.dto;

import com.ecommerce.portal.model.ServiceRole;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pattern: response body for service lookups/listing. Never carries the
 * client secret or its hash - only {@link ServiceCreatedResponse} (register)
 * and {@link RotateSecretResponse} (rotate) ever return the plaintext
 * secret, and only once.
 */
public record ServiceResponse(
        UUID id,
        String name,
        String displayName,
        String baseUrl,
        ServiceRole role,
        String clientId,
        boolean active,
        Instant createdAt
) {
}
