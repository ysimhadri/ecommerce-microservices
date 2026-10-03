package com.ecommerce.eligibility.dto;

/**
 * Credit-bureau payload. {@code available=false} is the circuit-breaker
 * fallback signal — the engine maps it to MANUAL_REVIEW_REQUIRED.
 */
public record CreditBureauReport(
        String customerId,
        Integer score,
        boolean available,
        String detail
) {
    public static CreditBureauReport scored(String customerId, int score) {
        return new CreditBureauReport(customerId, score, true, "ok");
    }

    public static CreditBureauReport unavailable(String customerId, String detail) {
        return new CreditBureauReport(customerId, null, false, detail);
    }
}
