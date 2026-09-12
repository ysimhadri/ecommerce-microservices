package com.ecommerce.portal.exception;

/**
 * Thrown by {@code ServiceRegistryService.register} when the requested
 * service name is already taken. Mapped to {@code 409 Conflict} by
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateServiceNameException extends RuntimeException {

    public DuplicateServiceNameException(String name) {
        super("A service is already registered with name: " + name);
    }
}
