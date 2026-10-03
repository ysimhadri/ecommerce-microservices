package com.ecommerce.cart.exception;

public class CartEmptyException extends RuntimeException {
    public CartEmptyException() {
        super("Cannot check out an empty cart");
    }
}
