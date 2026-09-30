package com.ecommerce.cart.exception;

public class CartForbiddenException extends RuntimeException {

    public CartForbiddenException(String message) {
        super(message);
    }
}
