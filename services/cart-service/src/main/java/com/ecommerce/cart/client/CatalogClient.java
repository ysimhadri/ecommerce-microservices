package com.ecommerce.cart.client;

import com.ecommerce.cart.dto.CatalogProduct;
import com.ecommerce.cart.exception.CatalogUnavailableException;
import com.ecommerce.cart.exception.InvalidProductException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;

/**
 * Synchronous read of product-catalog-service's public
 * {@code GET /api/v1/catalog/products/{id}}. Blocking WebClient; no retry or
 * circuit breaker in v1 - any failure other than 404 surfaces as a 500.
 */
@Component
public class CatalogClient {

    private final WebClient webClient;

    public CatalogClient(@Value("${app.catalog.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public CatalogProduct getProduct(UUID productId) {
        try {
            CatalogProduct product = webClient.get()
                    .uri("/api/v1/catalog/products/{id}", productId)
                    .retrieve()
                    .bodyToMono(CatalogProduct.class)
                    .block(Duration.ofSeconds(5));
            if (product == null) {
                throw new InvalidProductException(productId);
            }
            return product;
        } catch (WebClientResponseException.NotFound e) {
            throw new InvalidProductException(productId);
        } catch (InvalidProductException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CatalogUnavailableException("Catalog service call failed", e);
        }
    }
}
