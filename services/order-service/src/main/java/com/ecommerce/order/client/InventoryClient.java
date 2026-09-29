package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.ReservationSnapshot;
import com.ecommerce.order.client.ClientSnapshots.ReserveLine;

import java.util.List;
import java.util.UUID;

/** Port the saga uses to talk to inventory-service. Tests substitute a fake. */
public interface InventoryClient {

    ReservationSnapshot reserve(UUID orderId, List<ReserveLine> lines, String bearerToken);

    void release(UUID reservationId, String bearerToken);

    void commit(UUID reservationId, String bearerToken);

    void revert(UUID reservationId, String bearerToken);
}
