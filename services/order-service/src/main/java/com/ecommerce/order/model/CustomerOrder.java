package com.ecommerce.order.model;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The order aggregate. Cancelled orders stay in the table; nothing here
 * deletes a row or its lines. Status moves {@code PENDING} → {@code CONFIRMED}
 * or {@code PENDING} → {@code CANCELLED}.
 */
@Entity
@Table(name = "orders")
public class CustomerOrder implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "cart_id", nullable = false, updatable = false)
    private UUID cartId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private OrderStatus status;

    @Transient
    private boolean isNew = true;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<OrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {
        // JPA
    }

    public CustomerOrder(UUID ownerId, UUID cartId) {
        this.ownerId = ownerId;
        this.cartId = cartId;
        this.status = OrderStatus.PENDING;
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

    public UUID getCartId() {
        return cartId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public void addLine(OrderLine line) {
        line.setOrder(this);
        this.lines.add(line);
        this.total = this.total.add(line.getLineTotal());
    }

    public void confirm() {
        if (this.status == OrderStatus.CONFIRMED) {
            return;
        }
        this.status = OrderStatus.CONFIRMED;
        this.failureCode = null;
        this.updatedAt = Instant.now();
    }

    public void cancel(String failureCode) {
        this.status = OrderStatus.CANCELLED;
        this.failureCode = failureCode;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerOrder other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
