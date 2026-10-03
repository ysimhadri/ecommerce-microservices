package com.ecommerce.eligibility.exception;

import java.time.Instant;

/**
 * Uniform JSON error body — callers never see a stack trace, only
 * {@code code}/{@code message}/{@code timestamp}.
 */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}
