package com.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One stock row per catalog product. {@link Version} is the optimistic lock:
 * two concurrent holds of the same row cannot both commit a stale available
 * count. {@code available} is free to promise; {@code reserved} is held for
 * an order that has not committed yet.
 */
@Entity
@Table(name = "stock")
public class Stock implements Persistable<UUID> {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Version
    @Column(nullable = false)
    private long version;

    @Transient
    private boolean isNew = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Stock() {
        // JPA
    }

    public Stock(UUID productId, int available) {
        this.productId = productId;
        this.available = available;
        this.reserved = 0;
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return productId;
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

    public UUID getProductId() {
        return productId;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
        this.updatedAt = Instant.now();
    }

    public int getReserved() {
        return reserved;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void hold(int quantity) {
        this.available -= quantity;
        this.reserved += quantity;
        this.updatedAt = Instant.now();
    }

    /** Compensation for hold: available restored, reserved released. */
    public void release(int quantity) {
        this.available += quantity;
        this.reserved -= quantity;
        this.updatedAt = Instant.now();
    }

    /** Commit keeps available down and drops the reserved count. */
    public void commit(int quantity) {
        this.reserved -= quantity;
        this.updatedAt = Instant.now();
    }

    /** Compensation for commit: available restored. Reserved was already dropped. */
    public void revert(int quantity) {
        this.available += quantity;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stock other)) return false;
        return Objects.equals(productId, other.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(productId);
    }
}
