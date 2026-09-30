package com.ecommerce.eligibility.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(String customerId) {
        super("Customer profile not found: " + customerId);
    }
}
