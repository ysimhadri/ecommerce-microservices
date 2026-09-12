package com.ecommerce.portal.exception;

import java.util.UUID;

/**
 * Thrown when a service id in a path variable or request body does not
 * reference a registered service. Mapped to {@code 404 Not Found} by
 * {@link GlobalExceptionHandler}.
 */
public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(UUID id) {
        super("No registered service exists with id: " + id);
    }
}
