package com.ecommerce.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the User/Auth microservice - the first service in the
 * ecommerce services/ monorepo. Owns a single PostgreSQL datastore and
 * issues stateless JWT access tokens; no other service reaches into its
 * database or its session state (there is none).
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
