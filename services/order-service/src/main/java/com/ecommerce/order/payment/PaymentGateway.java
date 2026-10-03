package com.ecommerce.order.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment step of the saga. The only implementation in this repo is the mock;
 * a real provider stays deferred.
 */
public interface PaymentGateway {

    void authorize(UUID orderId, BigDecimal amount, boolean simulatePaymentFailure);

    void voidAuthorization(UUID orderId);
}
