package com.ecommerce.inventory.model;

/**
 * Reservation lifecycle the order saga drives.
 * Hold moves stock from available into reserved ({@code HELD}).
 * Release restores available. Commit drops the reserved count and leaves
 * available down. Revert puts those units back into available.
 */
public enum ReservationStatus {
    HELD,
    RELEASED,
    COMMITTED,
    REVERTED
}
