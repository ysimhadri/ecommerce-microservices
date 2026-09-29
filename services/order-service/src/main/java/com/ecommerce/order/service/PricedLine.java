package com.ecommerce.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Catalog name and price snapshotted onto an order line before any remote side effect. */
public record PricedLine(UUID productId, String productName, BigDecimal unitPrice, int quantity) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
