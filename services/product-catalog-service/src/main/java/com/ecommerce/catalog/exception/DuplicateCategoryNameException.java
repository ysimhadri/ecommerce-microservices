package com.ecommerce.catalog.exception;

/**
 * Thrown by {@code CategoryCommandService.create} when the requested
 * category name is already taken. Mapped to {@code 409 Conflict} by
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String name) {
        super("Category name already exists: " + name);
    }
}
