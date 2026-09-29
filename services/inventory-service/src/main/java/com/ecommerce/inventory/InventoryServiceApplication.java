package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Inventory microservice. Owns a single PostgreSQL
 * datastore ({@code inventorydb}) for stock and reservations. Catalog has
 * no stock column and is not written by this service.
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
