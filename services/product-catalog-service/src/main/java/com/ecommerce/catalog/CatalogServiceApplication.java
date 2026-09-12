package com.ecommerce.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Product Catalog microservice. Owns a single
 * PostgreSQL datastore (no sharing with auth-service or any other
 * service) and exposes categories/products under {@code /api/v1/catalog}.
 * v1 is read-public, write-open: it does not validate JWTs issued by
 * auth-service (see README for the rationale) - that is the natural next
 * step once a gateway/shared auth story exists.
 */
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
