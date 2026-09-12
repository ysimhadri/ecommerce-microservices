package com.ecommerce.portal.exception;

/**
 * Thrown by {@code TokenService.issueToken} when the requested
 * {@code audience} does not name an active, registered PRODUCER (or BOTH)
 * service. Mapped to {@code 400 Bad Request} - the token request itself is
 * malformed, not merely unauthorized - by {@link GlobalExceptionHandler}.
 */
public class UnknownAudienceException extends RuntimeException {

    public UnknownAudienceException(String audience) {
        super("Unknown or non-producer audience: " + audience);
    }
}
