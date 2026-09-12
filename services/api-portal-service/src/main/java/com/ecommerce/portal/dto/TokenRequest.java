package com.ecommerce.portal.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * DTO pattern: request payload for {@code POST /api/v1/portal/oauth/token}
 * (the client-credentials grant). {@code scope} is optional and, when
 * omitted or empty, means "issue every scope this consumer is granted for
 * this audience" - when present, the issued token carries the intersection
 * of requested and granted scopes (see {@code TokenService}).
 */
public record TokenRequest(

        @NotBlank(message = "clientId is required")
        String clientId,

        @NotBlank(message = "clientSecret is required")
        String clientSecret,

        @NotBlank(message = "audience is required")
        String audience,

        List<String> scope
) {
}
