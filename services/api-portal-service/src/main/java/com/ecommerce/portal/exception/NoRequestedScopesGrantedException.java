package com.ecommerce.portal.exception;

/**
 * Thrown by {@code TokenService.issueToken} when none of the explicitly
 * requested scopes are present in the consumer's grant for that producer -
 * an empty requested-scope list is not this case (it means "issue every
 * granted scope"; see {@code TokenService}). Mapped to
 * {@code 403 Forbidden} by {@link GlobalExceptionHandler}.
 */
public class NoRequestedScopesGrantedException extends RuntimeException {

    public NoRequestedScopesGrantedException(String consumerName, String producerName) {
        super("None of the requested scopes are granted to " + consumerName + " for " + producerName);
    }
}
