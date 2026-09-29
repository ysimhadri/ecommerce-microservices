package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.CartSnapshot;
import com.ecommerce.order.exception.OrderExceptions.CartForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.CartNotActiveException;
import com.ecommerce.order.exception.OrderExceptions.CartNotFoundException;
import com.ecommerce.order.exception.OrderExceptions.RemoteCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class RestCartClient implements CartClient {

    private final RestClient cartRestClient;
    private final ObjectMapper objectMapper;

    public RestCartClient(@Qualifier("cartRestClient") RestClient cartRestClient, ObjectMapper objectMapper) {
        this.cartRestClient = cartRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public CartSnapshot getCart(UUID cartId, String bearerToken) {
        return authorized(cartRestClient.get().uri("/api/v1/carts/{id}", cartId), bearerToken)
                .body(CartSnapshot.class);
    }

    @Override
    public void lock(UUID cartId, String bearerToken) {
        authorized(cartRestClient.post().uri("/api/v1/carts/{id}/lock", cartId), bearerToken).toBodilessEntity();
    }

    @Override
    public void unlock(UUID cartId, String bearerToken) {
        authorized(cartRestClient.post().uri("/api/v1/carts/{id}/unlock", cartId), bearerToken).toBodilessEntity();
    }

    @Override
    public void clear(UUID cartId, String bearerToken) {
        authorized(cartRestClient.post().uri("/api/v1/carts/{id}/clear", cartId), bearerToken).toBodilessEntity();
    }

    @Override
    public void restore(UUID cartId, String bearerToken) {
        authorized(cartRestClient.post().uri("/api/v1/carts/{id}/restore", cartId), bearerToken).toBodilessEntity();
    }

    private RestClient.ResponseSpec authorized(RestClient.RequestHeadersSpec<?> spec, String bearerToken) {
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw map(response.getStatusCode().value(), RemoteResponses.errorCode(response, objectMapper));
                });
    }

    private RuntimeException map(int status, String code) {
        if (status == 404 || "CART_NOT_FOUND".equals(code)) {
            return new CartNotFoundException("Cart not found");
        }
        if (status == 403 || "CART_FORBIDDEN".equals(code)) {
            return new CartForbiddenException("Cart belongs to another user");
        }
        if ("CART_NOT_ACTIVE".equals(code) || "CART_INVALID_STATE".equals(code)) {
            return new CartNotActiveException("Cart is not active");
        }
        return new RemoteCallException("Cart service call failed (" + status + ")");
    }
}
