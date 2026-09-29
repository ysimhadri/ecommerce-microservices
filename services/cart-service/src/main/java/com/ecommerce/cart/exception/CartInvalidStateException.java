package com.ecommerce.cart.exception;

public class CartInvalidStateException extends RuntimeException {

    public CartInvalidStateException(String message) {
        super(message);
    }
}
