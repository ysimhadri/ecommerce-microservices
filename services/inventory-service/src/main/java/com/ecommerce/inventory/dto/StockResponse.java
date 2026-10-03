package com.ecommerce.inventory.dto;

import java.util.UUID;

/** DTO pattern: current free and held counts for one product. */
public record StockResponse(UUID productId, int available, int reserved) {
}
