package com.ecommerce.portal.model;

/**
 * The capacity a registered microservice acts in: it may call other
 * services (CONSUMER), be called by other services (PRODUCER), or both.
 * Drives the authorization checks in the registry/grant/token flows -
 * see {@link #isProducer()} / {@link #isConsumer()}.
 */
public enum ServiceRole {
    PRODUCER,
    CONSUMER,
    BOTH;

    public boolean isProducer() {
        return this == PRODUCER || this == BOTH;
    }

    public boolean isConsumer() {
        return this == CONSUMER || this == BOTH;
    }
}
