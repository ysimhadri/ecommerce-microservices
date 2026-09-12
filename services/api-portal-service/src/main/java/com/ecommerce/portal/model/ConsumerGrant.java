package com.ecommerce.portal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity backing the {@code consumer_grants} table: one row per
 * consumer-service-to-producer-service authorization, auto-approved at
 * creation time (v1 has no human approval workflow). {@code scopes} is a
 * space-delimited string, same rationale as {@link ProducerApi#getRequiredScopes()}.
 * A unique (consumer, producer) constraint at the DB level means exactly
 * one grant per pair - see the migration for why.
 *
 * <p>Implements {@link Persistable} for the same reason as {@code User}/
 * {@code Category}/{@code RegisteredService}: a client-assigned UUID id
 * defeats Spring Data JPA's default {@code isNew()} heuristic. See those
 * entities' javadoc for the full explanation.</p>
 */
@Entity
@Table(name = "consumer_grants")
public class ConsumerGrant implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean isNew = true;

    @Column(name = "consumer_service_id", nullable = false)
    private UUID consumerServiceId;

    @Column(name = "producer_service_id", nullable = false)
    private UUID producerServiceId;

    @Column(nullable = false)
    private String scopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConsumerGrant() {
        // JPA
    }

    public ConsumerGrant(UUID consumerServiceId, UUID producerServiceId, String scopes) {
        this.consumerServiceId = consumerServiceId;
        this.producerServiceId = producerServiceId;
        this.scopes = scopes;
        this.createdAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getConsumerServiceId() {
        return consumerServiceId;
    }

    public UUID getProducerServiceId() {
        return producerServiceId;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsumerGrant other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
