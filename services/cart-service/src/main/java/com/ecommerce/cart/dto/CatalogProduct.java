package com.ecommerce.cart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** The subset of product-catalog-service's ProductResponse this service reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProduct(UUID id, String name, BigDecimal price) {
}
