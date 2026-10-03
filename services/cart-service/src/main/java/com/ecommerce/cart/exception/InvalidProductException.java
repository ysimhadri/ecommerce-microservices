package com.ecommerce.cart.exception;

import java.util.UUID;

public class InvalidProductException extends RuntimeException {
    public InvalidProductException(UUID productId) {
        super("Unknown product id: " + productId);
    }
}
