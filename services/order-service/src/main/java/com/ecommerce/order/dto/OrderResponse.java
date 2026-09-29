package com.ecommerce.order.dto;

import com.ecommerce.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Successful placement (201) and order lookup. {@code failureCode} is set only when cancelled. */
public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        UUID cartId,
        BigDecimal total,
        List<OrderLineResponse> lines,
        String failureCode
) {
}
