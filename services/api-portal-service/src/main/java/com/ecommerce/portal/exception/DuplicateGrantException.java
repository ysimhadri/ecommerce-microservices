package com.ecommerce.portal.exception;

/**
 * Thrown by {@code GrantService.createGrant} when a grant already exists
 * for the given (consumer, producer) pair - v1 allows exactly one grant per
 * pair (see the migration comment). Mapped to {@code 409 Conflict} by
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateGrantException extends RuntimeException {

    public DuplicateGrantException(String consumerName, String producerName) {
        super("A grant already exists from " + consumerName + " to " + producerName);
    }
}
