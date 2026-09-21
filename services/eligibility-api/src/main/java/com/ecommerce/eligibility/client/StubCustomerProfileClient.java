package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CustomerProfileResponse;
import com.ecommerce.eligibility.exception.CustomerNotFoundException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * In-process stand-in for {@code customer-profile-service}. Used whenever
 * that service (and Eureka) are not running — tests, local demos, and
 * docker-compose of this module alone.
 */
@Component
public class StubCustomerProfileClient implements CustomerProfileClient {

    @Override
    public CustomerProfileResponse getProfile(String customerId) {
        if (customerId == null || customerId.isBlank() || customerId.equalsIgnoreCase("unknown")) {
            throw new CustomerNotFoundException(customerId == null ? "null" : customerId);
        }
        String kyc = customerId.toLowerCase().contains("unverified") ? "PENDING" : "VERIFIED";
        BigDecimal income = customerId.toLowerCase().contains("low-income")
                ? new BigDecimal("18000")
                : new BigDecimal("95000");
        return new CustomerProfileResponse(customerId, "Demo Customer " + customerId, kyc, income);
    }
}
