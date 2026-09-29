package com.ecommerce.cart.model;

/**
 * Cart lifecycle the order saga drives.
 * {@code ACTIVE} accepts items. {@code LOCKED} is held for checkout.
 * {@code CHECKED_OUT} hides lines in the API while keeping them stored so
 * restore can return the same cart to {@code ACTIVE}.
 */
public enum CartStatus {
    ACTIVE,
    LOCKED,
    CHECKED_OUT
}
