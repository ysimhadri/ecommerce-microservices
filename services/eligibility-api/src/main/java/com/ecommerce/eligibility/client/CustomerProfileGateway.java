package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CustomerProfileResponse;
import com.ecommerce.eligibility.exception.CustomerNotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Primary {@link CustomerProfileClient} used by the eligibility engine.
 *
 * <p>Feign is the default remote path ({@code app.customer-profile.client=feign}).
 * The {@code @LoadBalanced} RestTemplate is kept wired as a real alternative
 * ({@code client=rest}) that calls {@code http://customer-profile-service/...}
 * by Eureka name. Local/test set {@code app.customer-profile.stub=true} so
 * neither remote path is required.
 */
@Service
public class CustomerProfileGateway implements CustomerProfileClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerProfileGateway.class);

    private final CustomerProfileFeignClient feignClient;
    private final RestTemplate loadBalancedRestTemplate;
    private final StubCustomerProfileClient stubClient;
    private final boolean stub;
    private final String clientMode;

    public CustomerProfileGateway(
            CustomerProfileFeignClient feignClient,
            @Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            StubCustomerProfileClient stubClient,
            @Value("${app.customer-profile.stub:true}") boolean stub,
            @Value("${app.customer-profile.client:feign}") String clientMode) {
        this.feignClient = feignClient;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.stubClient = stubClient;
        this.stub = stub;
        this.clientMode = clientMode;
    }

    @Override
    public CustomerProfileResponse getProfile(String customerId) {
        if (stub) {
            return stubClient.getProfile(customerId);
        }
        try {
            if ("rest".equalsIgnoreCase(clientMode)) {
                log.debug("Fetching customer profile via load-balanced RestTemplate");
                return loadBalancedRestTemplate.getForObject(
                        "http://customer-profile-service/api/v1/customers/{id}",
                        CustomerProfileResponse.class,
                        customerId);
            }
            return feignClient.getProfile(customerId);
        } catch (FeignException.NotFound | HttpClientErrorException.NotFound ex) {
            throw new CustomerNotFoundException(customerId);
        }
    }
}
