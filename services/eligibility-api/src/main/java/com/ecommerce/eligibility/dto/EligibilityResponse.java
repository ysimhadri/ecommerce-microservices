package com.ecommerce.eligibility.dto;

import com.ecommerce.eligibility.model.EligibilityStatus;
import com.ecommerce.eligibility.model.ProductType;

import java.time.Instant;

/**
 * API + cache payload. Never expose JPA entities on the wire.
 */
public record EligibilityResponse(
        String customerId,
        ProductType productType,
        EligibilityStatus status,
        String reason,
        Integer creditScore,
        Instant evaluatedAt
) {
    public boolean manualReview() {
        return status == EligibilityStatus.MANUAL_REVIEW_REQUIRED;
    }
}
