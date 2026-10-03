package com.ecommerce.eligibility.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Keeps a {@code @LoadBalanced} RestTemplate configured for calling
 * internal services by Eureka name (see {@code CustomerProfileGateway}
 * {@code client=rest} path). A second, non-load-balanced template is used
 * for the external credit bureau so those calls are not rewritten by
 * Spring Cloud LoadBalancer.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate externalRestTemplate() {
        return new RestTemplate();
    }
}
