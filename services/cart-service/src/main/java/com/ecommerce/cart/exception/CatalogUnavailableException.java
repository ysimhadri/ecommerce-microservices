package com.ecommerce.cart.exception;

/** Catalog call failed for a reason other than "product not found"; surfaces as a 500. */
public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
