package com.ecommerce.inventory.dto;

import java.util.UUID;

public record ReservationLineResponse(UUID productId, int quantity) {
}
