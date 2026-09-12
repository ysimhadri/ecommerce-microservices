package com.ecommerce.catalog.model;

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
 * JPA entity backing the {@code categories} table. Kept separate from the
 * request/response DTOs (see the {@code dto} package) like every entity in
 * this monorepo.
 *
 * <p>Implements {@link Persistable} because {@code id} is a client-assigned
 * UUID (not {@code @GeneratedValue}): without this, Spring Data JPA's
 * default {@code isNew()} heuristic sees a non-null id at save() time and
 * treats every create as an update, routing {@code save()} through
 * {@code merge()} instead of {@code persist()} - and even {@code persist()}
 * alone would still defer the INSERT to flush/commit time for an
 * assigned-id entity, so {@link CategoryCommandService} also needs
 * {@code saveAndFlush(...)} rather than plain {@code save(...)}. Together
 * these force the INSERT (and any constraint violation it throws)
 * synchronously, inside the try/catch that expects it - see
 * {@code auth-service}'s {@code User} entity for the same fix, verified
 * empirically with a real concurrency test.</p>
 */
@Entity
@Table(name = "categories")
public class Category implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

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
