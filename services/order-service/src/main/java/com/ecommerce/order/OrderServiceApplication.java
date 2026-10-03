package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Order microservice. Owns {@code orderdb} and the
 * in-process saga that places an order by calling cart, inventory, and
 * catalog over HTTP. There is no message broker and no shared database.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
