package com.ecommerce.catalog.exception;

import java.time.Instant;

/**
 * Uniform JSON error body returned by {@link GlobalExceptionHandler} -
 * callers never see a stack trace or internal detail, only
 * {@code code}/{@code message}/{@code timestamp}.
 */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}
