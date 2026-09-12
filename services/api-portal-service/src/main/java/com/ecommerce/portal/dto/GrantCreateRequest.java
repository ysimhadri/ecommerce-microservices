package com.ecommerce.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTO pattern: request payload for {@code POST /api/v1/portal/grants}.
 * Auto-approved on creation - v1 has no human approval workflow.
 */
public record GrantCreateRequest(

        @NotNull(message = "consumerServiceId is required")
        UUID consumerServiceId,

        @NotNull(message = "producerServiceId is required")
        UUID producerServiceId,

        @NotEmpty(message = "scopes must contain at least one scope")
        List<@NotBlank(message = "scopes entries must not be blank") String> scopes
) {
}
