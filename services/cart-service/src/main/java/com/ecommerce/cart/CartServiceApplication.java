package com.ecommerce.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Cart microservice. Owns a single PostgreSQL datastore
 * ({@code cartdb}) and the cart aggregate an order saga locks, clears, and
 * restores. It verifies {@code auth-service} access tokens locally and never
 * calls auth-service over the network.
 */
@SpringBootApplication
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
