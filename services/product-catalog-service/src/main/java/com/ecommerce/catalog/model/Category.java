package com.ecommerce.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity backing the {@code categories} table. Kept separate from the
 * request/response DTOs (see the {@code dto} package) like every entity in
 * this monorepo.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
        // JPA
    }

    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        // Set eagerly rather than via @CreationTimestamp so the value is
        // already on this in-memory instance immediately after save() -
        // @CreationTimestamp only populates the field at flush time, which
        // is too late for the command service to return it.
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
