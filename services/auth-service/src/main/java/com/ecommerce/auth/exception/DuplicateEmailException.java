package com.ecommerce.auth.exception;

/**
 * Thrown by {@code AuthService.register} when the requested email is
 * already taken. Mapped to {@code 409 Conflict} by
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Email already registered: " + email);
    }
}
