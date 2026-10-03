package com.ecommerce.payment.dto;

/** {@code created} is false when the same order, user, amount, and currency are replayed. */
public record AuthorizeOutcome(PaymentResponse payment, boolean created) {
}
