package com.ecommerce.cart.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity backing the {@code carts} table. Lines stay on the aggregate
 * for the life of the cart — clear never removes them.
 */
@Entity
@Table(name = "carts")
public class Cart implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private CartStatus status;

    /** Assigned ids are non-null before insert. See auth-service {@code User}. */
    @Transient
    private boolean isNew = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<CartLine> lines = new ArrayList<>();

    protected Cart() {
        // JPA
    }

    public Cart(UUID ownerId) {
        this.ownerId = ownerId;
        this.status = CartStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
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

    public UUID getOwnerId() {
        return ownerId;
    }

    public CartStatus getStatus() {
        return status;
    }

    public void setStatus(CartStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartLine> getLines() {
        return lines;
    }

    public void addLine(CartLine line) {
        line.setCart(this);
        this.lines.add(line);
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cart other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
