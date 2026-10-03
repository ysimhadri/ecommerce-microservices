package com.ecommerce.order.payment;

import com.ecommerce.order.client.RestPaymentClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapts the saga to {@link RestPaymentClient}. The client is a separate bean
 * so {@code @CircuitBreaker} crosses a Spring proxy. Orders have no currency
 * column; every charge is sent as USD. {@code simulatePaymentFailure} is
 * forwarded as {@code simulateDecline}.
 */
@Service
public class HttpPaymentGateway implements PaymentGateway {

    static final String CURRENCY = "USD";

    private final RestPaymentClient restPaymentClient;

    public HttpPaymentGateway(RestPaymentClient restPaymentClient) {
        this.restPaymentClient = restPaymentClient;
    }

    @Override
    public void authorize(UUID orderId, BigDecimal amount, boolean simulatePaymentFailure, String bearerToken) {
        restPaymentClient.authorize(orderId, amount, CURRENCY, simulatePaymentFailure, bearerToken);
    }

    @Override
    public void voidAuthorization(UUID orderId, String bearerToken) {
        restPaymentClient.voidAuthorization(orderId, bearerToken);
    }
}
