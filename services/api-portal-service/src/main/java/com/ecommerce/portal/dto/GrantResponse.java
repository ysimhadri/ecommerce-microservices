package com.ecommerce.portal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO pattern: response body for grant creation and listing. Includes both
 * services' names alongside their ids purely for readability - callers
 * otherwise only ever get ids back from this API.
 */
public record GrantResponse(
        UUID id,
        UUID consumerServiceId,
        String consumerServiceName,
        UUID producerServiceId,
        String producerServiceName,
        List<String> scopes,
        Instant createdAt
) {
}
