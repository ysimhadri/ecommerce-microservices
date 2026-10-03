package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.CatalogProductSnapshot;
import com.ecommerce.order.exception.OrderExceptions.ProductNotFoundException;
import com.ecommerce.order.exception.OrderExceptions.RemoteCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class RestCatalogClient implements CatalogClient {

    private final RestClient catalogRestClient;
    private final ObjectMapper objectMapper;

    public RestCatalogClient(@Qualifier("catalogRestClient") RestClient catalogRestClient, ObjectMapper objectMapper) {
        this.catalogRestClient = catalogRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public CatalogProductSnapshot getProduct(UUID productId) {
        return catalogRestClient.get()
                .uri("/api/v1/catalog/products/{id}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    int status = response.getStatusCode().value();
                    String code = RemoteResponses.errorCode(response, objectMapper);
                    if (status == 404 || "PRODUCT_NOT_FOUND".equals(code)) {
                        throw new ProductNotFoundException("Product not found");
                    }
                    throw new RemoteCallException("Catalog service call failed");
                })
                .body(CatalogProductSnapshot.class);
    }
}
