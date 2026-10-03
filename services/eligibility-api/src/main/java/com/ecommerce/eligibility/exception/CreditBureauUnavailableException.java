package com.ecommerce.eligibility.exception;

public class CreditBureauUnavailableException extends RuntimeException {

    public CreditBureauUnavailableException(String message) {
        super(message);
    }

    public CreditBureauUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
