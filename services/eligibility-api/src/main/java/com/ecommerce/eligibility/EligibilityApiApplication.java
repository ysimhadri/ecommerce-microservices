package com.ecommerce.eligibility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Eligibility API microservice: decides whether a customer can take a
 * financial product. Owns its own H2 datastore (no sharing with
 * auth/catalog/portal) and registers with Eureka so it can discover
 * {@code customer-profile-service} by name. Eureka is switched off on the
 * {@code test} profile so {@code mvn test} runs fully offline.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.ecommerce.eligibility.client")
@EnableDiscoveryClient
public class EligibilityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EligibilityApiApplication.class, args);
    }
}
