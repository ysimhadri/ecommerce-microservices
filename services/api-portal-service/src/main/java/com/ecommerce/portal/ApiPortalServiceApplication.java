package com.ecommerce.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Portal microservice - service-to-service auth
 * only. It issues short-lived HS256 client-credentials JWTs so one internal
 * microservice can call another's protected endpoints; it never issues or
 * validates tokens for human users (that remains {@code auth-service}'s
 * job). Owns a single PostgreSQL datastore (no sharing with any other
 * service) and exposes registration/grant/token APIs under
 * {@code /api/v1/portal}.
 */
@SpringBootApplication
public class ApiPortalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiPortalServiceApplication.class, args);
    }
}
