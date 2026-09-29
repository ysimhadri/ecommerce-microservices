package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.ReservationSnapshot;
import com.ecommerce.order.client.ClientSnapshots.ReserveLine;
import com.ecommerce.order.exception.OrderExceptions.InsufficientStockException;
import com.ecommerce.order.exception.OrderExceptions.RemoteCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class RestInventoryClient implements InventoryClient {

    private final RestClient inventoryRestClient;
    private final ObjectMapper objectMapper;

    public RestInventoryClient(@Qualifier("inventoryRestClient") RestClient inventoryRestClient,
                                ObjectMapper objectMapper) {
        this.inventoryRestClient = inventoryRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReservationSnapshot reserve(UUID orderId, List<ReserveLine> lines, String bearerToken) {
        return call(inventoryRestClient.post()
                .uri("/api/v1/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReserveBody(orderId, lines)), bearerToken)
                .body(ReservationSnapshot.class);
    }

    @Override
    public void release(UUID reservationId, String bearerToken) {
        call(inventoryRestClient.post().uri("/api/v1/inventory/reservations/{id}/release", reservationId), bearerToken)
                .toBodilessEntity();
    }

    @Override
    public void commit(UUID reservationId, String bearerToken) {
        call(inventoryRestClient.post().uri("/api/v1/inventory/reservations/{id}/commit", reservationId), bearerToken)
                .toBodilessEntity();
    }

    @Override
    public void revert(UUID reservationId, String bearerToken) {
        call(inventoryRestClient.post().uri("/api/v1/inventory/reservations/{id}/revert", reservationId), bearerToken)
                .toBodilessEntity();
    }

    private RestClient.ResponseSpec call(RestClient.RequestHeadersSpec<?> spec, String bearerToken) {
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String code = RemoteResponses.errorCode(response, objectMapper);
                    if ("INSUFFICIENT_STOCK".equals(code)) {
                        throw new InsufficientStockException("Insufficient stock to fill every line");
                    }
                    throw new RemoteCallException("Inventory service call failed (" + response.getStatusCode().value() + ")");
                });
    }

    private record ReserveBody(UUID orderId, List<ReserveLine> lines) {
    }
}
