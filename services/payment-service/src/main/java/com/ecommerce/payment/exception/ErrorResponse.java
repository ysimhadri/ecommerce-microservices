package com.ecommerce.payment.exception;

import java.time.Instant;

/** Uniform JSON error body. Controllers never return this from a success path. */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}
