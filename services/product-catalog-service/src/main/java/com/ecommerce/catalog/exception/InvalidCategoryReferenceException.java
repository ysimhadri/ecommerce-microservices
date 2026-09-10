package com.ecommerce.catalog.exception;

import java.util.UUID;

/**
 * Thrown by {@code ProductCommandService.create} when the request's
 * {@code categoryId} does not reference an existing category - a bad
 * request body, not a missing resource, so it is mapped to
 * {@code 400 Bad Request} (not 404) by {@link GlobalExceptionHandler}.
 */
public class InvalidCategoryReferenceException extends RuntimeException {

    public InvalidCategoryReferenceException(UUID categoryId) {
        super("No category exists with id: " + categoryId);
    }
}
