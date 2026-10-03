package com.ecommerce.cart.exception;

public class CartNotActiveException extends RuntimeException {
    public CartNotActiveException() {
        super("Cart is checked out and can no longer be modified");
    }
}
