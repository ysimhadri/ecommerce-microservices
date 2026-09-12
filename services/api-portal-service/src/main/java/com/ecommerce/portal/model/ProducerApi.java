package com.ecommerce.portal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity backing the {@code producer_apis} table: one row per API a
 * producer service has declared, and the scope(s) required to call it.
 * {@code producerServiceId} is a plain foreign-key column rather than a
 * {@code @ManyToOne} association - matches {@code Product.categoryId} in
 * product-catalog-service, keeping this entity free of lazy-loading
 * concerns. {@code requiredScopes} is stored as a single space-delimited
 * string (see {@link com.ecommerce.portal.util.Scopes}) rather than a join
 * table - simplest thing that works for v1's small per-API scope lists.
 */
@Entity
@Table(name = "producer_apis")
public class ProducerApi {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "producer_service_id", nullable = false)
    private UUID producerServiceId;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Column(name = "required_scopes", nullable = false)
    private String requiredScopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProducerApi() {
        // JPA
    }

    public ProducerApi(UUID producerServiceId, String method, String pathPattern, String requiredScopes) {
        this.producerServiceId = producerServiceId;
        this.method = method;
        this.pathPattern = pathPattern;
        this.requiredScopes = requiredScopes;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProducerServiceId() {
        return producerServiceId;
    }

    public String getMethod() {
        return method;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public String getRequiredScopes() {
        return requiredScopes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProducerApi other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
