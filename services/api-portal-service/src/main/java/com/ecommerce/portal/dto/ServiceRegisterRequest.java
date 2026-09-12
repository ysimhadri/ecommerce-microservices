package com.ecommerce.portal.dto;

import com.ecommerce.portal.model.ServiceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO pattern: request payload for {@code POST /api/v1/portal/services}.
 * {@code name} is the slug other calls address this service by - as the
 * grant {@code producerServiceId}/{@code consumerServiceId} target and as
 * the token endpoint's {@code audience} - so it is constrained to a
 * lower-kebab-case identifier, not free text.
 */
public record ServiceRegisterRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "name must be a lower-kebab-case slug, e.g. product-catalog-service")
        String name,

        @NotBlank(message = "displayName is required")
        @Size(max = 150, message = "displayName must be at most 150 characters")
        String displayName,

        @Size(max = 500, message = "baseUrl must be at most 500 characters")
        String baseUrl,

        @NotNull(message = "role is required")
        ServiceRole role
) {
}
