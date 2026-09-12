package com.ecommerce.portal.exception;

/**
 * Thrown when an operation that requires the CONSUMER (or BOTH) role -
 * being the subject of a grant, or requesting a client-credentials token -
 * is attempted against a service registered only as PRODUCER. Mapped to
 * {@code 403 Forbidden} by {@link GlobalExceptionHandler}.
 */
public class NotAConsumerException extends RuntimeException {

    public NotAConsumerException(String serviceName) {
        super("Service '" + serviceName + "' is not registered with the CONSUMER role");
    }
}
