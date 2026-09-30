package com.ecommerce.inventory.dto;

/** {@code created} is false when the same {@code orderId} is replayed and stock is not held again. */
public record HoldOutcome(ReservationResponse reservation, boolean created) {
}
