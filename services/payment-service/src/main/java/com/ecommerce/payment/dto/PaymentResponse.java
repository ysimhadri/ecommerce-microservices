package com.ecommerce.payment.dto;

import com.ecommerce.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Charge the saga and a direct caller both see. Controllers return this, never the entity. */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status
) {
}
