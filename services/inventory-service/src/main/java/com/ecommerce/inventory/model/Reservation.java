package com.ecommerce.inventory.model;

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

/** One reservation per order. {@code order_id} is unique so a replay cannot hold stock twice. */
@Entity
@Table(name = "reservations")
public class Reservation implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private ReservationStatus status;

    @Transient
    private boolean isNew = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<ReservationLine> lines = new ArrayList<>();

    protected Reservation() {
        // JPA
    }

    public Reservation(UUID orderId) {
        this.orderId = orderId;
        this.status = ReservationStatus.HELD;
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

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ReservationLine> getLines() {
        return lines;
    }

    public void addLine(ReservationLine line) {
        line.setReservation(this);
        this.lines.add(line);
    }

    public void markReleased() {
        this.status = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public void markCommitted() {
        this.status = ReservationStatus.COMMITTED;
        this.updatedAt = Instant.now();
    }

    public void markReverted() {
        this.status = ReservationStatus.REVERTED;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
