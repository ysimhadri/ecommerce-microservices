package com.ecommerce.portal.dto;

import java.util.UUID;

/**
 * DTO pattern: response body for
 * {@code POST /api/v1/portal/services/{id}/rotate-secret}. Same one-time
 * plaintext-secret contract as {@link ServiceCreatedResponse} - the old
 * secret stops working the instant this returns.
 */
public record RotateSecretResponse(
        UUID id,
        String clientId,
        String clientSecret
) {
}
