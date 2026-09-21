package com.ecommerce.eligibility.dto;

import java.math.BigDecimal;

/**
 * Shape returned by {@code customer-profile-service}. Kept as a DTO so Feign
 * and the load-balanced RestTemplate share one contract.
 */
public record CustomerProfileResponse(
        String customerId,
        String fullName,
        String kycStatus,
        BigDecimal annualIncome
) {
}
