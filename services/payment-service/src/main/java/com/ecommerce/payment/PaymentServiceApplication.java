package com.ecommerce.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Payment microservice. Owns {@code paymentdb} and a
 * simulated acquirer. The order saga calls authorize and void over HTTP.
 * There is no Stripe account, no broker, and no shared database.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
