package com.ecommerce.catalog.exception;

import java.util.UUID;

/**
 * Thrown when a product id looked up via {@code GET /api/v1/catalog/products/{id}}
 * does not exist. Mapped to {@code 404 Not Found} by
 * {@link GlobalExceptionHandler}.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found: " + id);
    }
}
