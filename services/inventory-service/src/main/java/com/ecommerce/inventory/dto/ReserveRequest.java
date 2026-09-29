package com.ecommerce.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** DTO pattern: hold stock for every line of one order, or return the existing hold. */
public record ReserveRequest(
        @NotNull(message = "must not be null") UUID orderId,
        @NotEmpty(message = "must not be empty") List<@Valid ReserveLineRequest> lines
) {
}
