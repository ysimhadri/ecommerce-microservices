package com.ecommerce.order.exception;

import java.time.Instant;

/**
 * Uniform JSON error body for ordinary failures. A compensated placement
 * does not use this type — see {@code OrderPlacementFailureResponse}.
 */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}
