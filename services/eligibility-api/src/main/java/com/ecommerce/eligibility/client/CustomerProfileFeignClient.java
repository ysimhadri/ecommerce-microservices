package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CustomerProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative client for {@code customer-profile-service}. Discovered by
 * Eureka name. Does not implement {@link CustomerProfileClient} so the Feign
 * proxy is not a competing injection candidate (Feign marks its beans
 * {@code @Primary}). {@link CustomerProfileGateway} is the single API used
 * by the engine.
 */
@FeignClient(name = "customer-profile-service")
public interface CustomerProfileFeignClient {

    @GetMapping("/api/v1/customers/{customerId}")
    CustomerProfileResponse getProfile(@PathVariable("customerId") String customerId);
}
