package com.ecommerce.order.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ClientSnapshots {

    private ClientSnapshots() {
    }

    public record CartSnapshot(UUID id, UUID ownerId, String status, List<CartLineSnapshot> lines) {
    }

    public record CartLineSnapshot(UUID productId, int quantity) {
    }

    public record CatalogProductSnapshot(UUID id, String name, BigDecimal price) {
    }

    public record ReservationSnapshot(UUID id, UUID orderId, String status) {
    }

    public record ReserveLine(UUID productId, int quantity) {
    }
}
