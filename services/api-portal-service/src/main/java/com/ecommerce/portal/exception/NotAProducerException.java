package com.ecommerce.portal.exception;

/**
 * Thrown when an operation that requires the PRODUCER (or BOTH) role -
 * declaring an API, or being granted to as the audience of a grant - is
 * attempted against a service registered only as CONSUMER. Mapped to
 * {@code 403 Forbidden} by {@link GlobalExceptionHandler}.
 */
public class NotAProducerException extends RuntimeException {

    public NotAProducerException(String serviceName) {
        super("Service '" + serviceName + "' is not registered with the PRODUCER role");
    }
}
