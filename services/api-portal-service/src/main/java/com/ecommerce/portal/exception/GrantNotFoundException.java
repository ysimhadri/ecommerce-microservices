package com.ecommerce.portal.exception;

/**
 * Thrown by {@code TokenService.issueToken} when the authenticated consumer
 * has no grant for the requested producer audience. Mapped to
 * {@code 403 Forbidden} by {@link GlobalExceptionHandler}.
 */
public class GrantNotFoundException extends RuntimeException {

    public GrantNotFoundException(String consumerName, String producerName) {
        super("No grant exists from " + consumerName + " to " + producerName);
    }
}
