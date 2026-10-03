package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CustomerProfileResponse;

/**
 * Lookup contract for {@code customer-profile-service}. Feign is the
 * primary implementation; a load-balanced RestTemplate path and a local
 * stub also implement this so tests and demos run without Eureka.
 */
public interface CustomerProfileClient {

    CustomerProfileResponse getProfile(String customerId);
}
