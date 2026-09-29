package com.ecommerce.cart.dto;

import java.util.UUID;

/** DTO pattern: one visible cart line. Product details are resolved later by the order saga. */
public record CartLineResponse(UUID productId, int quantity) {
}
