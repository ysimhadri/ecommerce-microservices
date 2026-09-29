package com.ecommerce.inventory.model;

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

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "reservation_lines",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reservation_id", "product_id"}))
public class ReservationLine implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Transient
    private boolean isNew = true;

    protected ReservationLine() {
        // JPA
    }

    public ReservationLine(UUID productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
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

    public Reservation getReservation() {
        return reservation;
    }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationLine other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
