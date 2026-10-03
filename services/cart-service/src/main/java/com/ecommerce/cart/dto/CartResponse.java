package com.ecommerce.cart.dto;

import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.model.CartStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID id, CartStatus status, List<CartItemResponse> items, BigDecimal total,
                           Instant createdAt, Instant updatedAt) {

    public static CartResponse from(Cart cart, List<CartItem> items) {
        BigDecimal total = items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), cart.getStatus(),
                items.stream().map(CartItemResponse::from).toList(), total,
                cart.getCreatedAt(), cart.getUpdatedAt());
    }
}
