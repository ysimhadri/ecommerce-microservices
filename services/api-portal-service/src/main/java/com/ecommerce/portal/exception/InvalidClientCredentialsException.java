package com.ecommerce.portal.exception;

/**
 * Thrown by {@code TokenService.issueToken} for an unknown clientId, a
 * wrong clientSecret, or an inactive service - all reported identically
 * (deliberately generic, same message either way) so the response never
 * discloses which one it was, mirroring {@code auth-service}'s
 * no-user-enumeration login failure. Mapped to {@code 401 Unauthorized} by
 * {@link GlobalExceptionHandler}.
 */
public class InvalidClientCredentialsException extends RuntimeException {

    public InvalidClientCredentialsException() {
        super("Invalid clientId or clientSecret");
    }
}
