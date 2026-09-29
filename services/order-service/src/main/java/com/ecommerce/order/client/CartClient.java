package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.CartSnapshot;

import java.util.UUID;

/** Port the saga uses to talk to cart-service. Tests substitute a fake. */
public interface CartClient {

    CartSnapshot getCart(UUID cartId, String bearerToken);

    void lock(UUID cartId, String bearerToken);

    void unlock(UUID cartId, String bearerToken);

    void clear(UUID cartId, String bearerToken);

    void restore(UUID cartId, String bearerToken);
}
