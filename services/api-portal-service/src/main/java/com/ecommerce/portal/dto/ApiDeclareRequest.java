package com.ecommerce.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO pattern: request payload for
 * {@code POST /api/v1/portal/services/{id}/apis} - a producer declaring one
 * API it exposes and the scope(s) a caller must present to invoke it.
 */
public record ApiDeclareRequest(

        @NotBlank(message = "method is required")
        @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "method must be one of GET, POST, PUT, PATCH, DELETE")
        String method,

        @NotBlank(message = "pathPattern is required")
        @Size(max = 500, message = "pathPattern must be at most 500 characters")
        String pathPattern,

        @NotEmpty(message = "requiredScopes must contain at least one scope")
        List<@NotBlank(message = "requiredScopes entries must not be blank") String> requiredScopes
) {
}
