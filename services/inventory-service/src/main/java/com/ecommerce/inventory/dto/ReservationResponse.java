package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.model.ReservationStatus;

import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        ReservationStatus status,
        List<ReservationLineResponse> lines
) {
}
