package com.ecommerce.cart.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One product line on a cart. Quantity only grows when the same product is added again. */
@Entity
@Table(name = "cart_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
public class CartLine implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Transient
    private boolean isNew = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartLine() {
        // JPA
    }

    public CartLine(UUID productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
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

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void increaseQuantity(int by) {
        this.quantity += by;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartLine other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
