package com.ecommerce.order.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment step of the saga. {@code HttpPaymentGateway} calls payment-service.
 * The saga depends only on this interface.
 */
public interface PaymentGateway {

    void authorize(UUID orderId, BigDecimal amount, boolean simulatePaymentFailure, String bearerToken);

    void voidAuthorization(UUID orderId, String bearerToken);
}
