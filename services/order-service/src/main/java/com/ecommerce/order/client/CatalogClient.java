package com.ecommerce.order.client;

import com.ecommerce.order.client.ClientSnapshots.CatalogProductSnapshot;

import java.util.UUID;

/** Port the saga uses for the public catalog product read. Tests substitute a fake. */
public interface CatalogClient {

    CatalogProductSnapshot getProduct(UUID productId);
}
