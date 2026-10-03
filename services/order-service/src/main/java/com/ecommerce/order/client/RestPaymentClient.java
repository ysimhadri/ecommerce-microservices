package com.ecommerce.order.client;

import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.exception.OrderExceptions.PaymentUnavailableException;
import com.ecommerce.order.exception.OrderExceptions.RemoteCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound payment calls. The circuit breaker records outages and ignores
 * declines. The fallback throws {@link PaymentUnavailableException} and does
 * not approve the charge. An open circuit fails immediately through that fallback.
 */
@Component
public class RestPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(RestPaymentClient.class);

    private final RestClient paymentRestClient;
    private final ObjectMapper objectMapper;

    public RestPaymentClient(@Qualifier("paymentRestClient") RestClient paymentRestClient,
                              ObjectMapper objectMapper) {
        this.paymentRestClient = paymentRestClient;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "payment", fallbackMethod = "authorizeFallback")
    public void authorize(UUID orderId, BigDecimal amount, String currency, boolean simulateDecline, String bearerToken) {
        execute(paymentRestClient.post()
                .uri("/api/v1/payments/authorizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AuthorizeBody(orderId, amount, currency, simulateDecline)), bearerToken);
    }

    @CircuitBreaker(name = "payment", fallbackMethod = "voidFallback")
    public void voidAuthorization(UUID orderId, String bearerToken) {
        execute(paymentRestClient.post().uri("/api/v1/payments/authorizations/{orderId}/void", orderId), bearerToken);
    }

    @SuppressWarnings("unused")
    public void authorizeFallback(UUID orderId, BigDecimal amount, String currency, boolean simulateDecline,
                                  String bearerToken, Throwable throwable) {
        throw failure(throwable);
    }

    @SuppressWarnings("unused")
    public void voidFallback(UUID orderId, String bearerToken, Throwable throwable) {
        throw failure(throwable);
    }

    private void execute(RestClient.RequestHeadersSpec<?> spec, String bearerToken) {
        try {
            call(spec, bearerToken).toBodilessEntity();
        } catch (PaymentDeclinedException | PaymentUnavailableException | RemoteCallException ex) {
            throw ex;
        } catch (RestClientException ex) {
            RuntimeException mapped = unwrap(ex);
            if (mapped != null) {
                throw mapped;
            }
            throw new PaymentUnavailableException("Payment service is unavailable", ex);
        }
    }

    private RestClient.ResponseSpec call(RestClient.RequestHeadersSpec<?> spec, String bearerToken) {
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new PaymentUnavailableException("Payment service is unavailable");
                })
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    String code = RemoteResponses.errorCode(response, objectMapper);
                    if ("PAYMENT_DECLINED".equals(code)) {
                        throw new PaymentDeclinedException("Payment was declined");
                    }
                    throw new RemoteCallException("Payment service call failed (" + response.getStatusCode().value() + ")");
                });
    }

    private static RuntimeException unwrap(RestClientException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof PaymentDeclinedException declined) {
                return declined;
            }
            if (current instanceof PaymentUnavailableException unavailable) {
                return unavailable;
            }
            if (current instanceof RemoteCallException remote) {
                return remote;
            }
            current = current.getCause();
        }
        return null;
    }

    private static RuntimeException failure(Throwable throwable) {
        log.warn("Payment client fallback: {}", throwable.toString());
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PaymentDeclinedException declined) {
                return declined;
            }
            if (current instanceof RemoteCallException remote) {
                return remote;
            }
            if (current instanceof PaymentUnavailableException unavailable) {
                return unavailable;
            }
            current = current.getCause();
        }
        return new PaymentUnavailableException("Payment service is unavailable", throwable);
    }

    private record AuthorizeBody(UUID orderId, BigDecimal amount, String currency, boolean simulateDecline) {
    }
}
