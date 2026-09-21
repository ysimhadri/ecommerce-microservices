package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CustomerProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative client for {@code customer-profile-service}. Discovered by
 * Eureka name unless {@code spring.cloud.openfeign.client.config.customer-profile-service.url}
 * is set. Local/test traffic goes through {@link CustomerProfileGateway}'s stub
 * so this client is not invoked offline.
 */
@FeignClient(name = "customer-profile-service")
public interface CustomerProfileFeignClient extends CustomerProfileClient {

    @Override
    @GetMapping("/api/v1/customers/{customerId}")
    CustomerProfileResponse getProfile(@PathVariable("customerId") String customerId);
}
