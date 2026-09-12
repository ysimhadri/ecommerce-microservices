package com.ecommerce.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Product Catalog microservice. Owns a single
 * PostgreSQL datastore (no sharing with auth-service or any other
 * service) and exposes categories/products under {@code /api/v1/catalog}.
 * Reads stay fully public; writes require a service-to-service JWT minted
 * by api-portal-service (see the {@code security} package and the README's
 * S2S section) - this service never validates auth-service's human-user
 * JWTs, which is a deliberately separate concern.
 */
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
