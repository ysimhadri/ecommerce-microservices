package com.ecommerce.order.dto;

import com.ecommerce.order.model.OrderStatus;

import java.util.UUID;

/**
 * 409 body for a placement that created an order and then compensated.
 * This is not {@code ErrorResponse}: the caller must see the order id.
 */
public record OrderPlacementFailureResponse(
        UUID orderId,
        OrderStatus status,
        String code,
        String message
) {
}
