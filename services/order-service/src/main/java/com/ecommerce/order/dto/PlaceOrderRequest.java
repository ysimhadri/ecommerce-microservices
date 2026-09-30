package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** DTO pattern: place the caller's cart. {@code simulatePaymentFailure} defaults to false. */
public record PlaceOrderRequest(
        @NotNull(message = "must not be null") UUID cartId,
        Boolean simulatePaymentFailure
) {
    public boolean simulatePaymentFailureOrDefault() {
        return Boolean.TRUE.equals(simulatePaymentFailure);
    }
}
