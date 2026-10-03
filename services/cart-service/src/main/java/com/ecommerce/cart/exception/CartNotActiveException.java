package com.ecommerce.cart.exception;

public class CartNotActiveException extends RuntimeException {

    public CartNotActiveException(String message) {
        super(message);
    }
}
