package com.ecommerce.cart.dto;

import com.ecommerce.cart.model.CartItem;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(UUID id, UUID productId, String productName, BigDecimal unitPrice,
                               int quantity, BigDecimal lineTotal) {

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getId(), item.getProductId(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.lineTotal());
    }
}
