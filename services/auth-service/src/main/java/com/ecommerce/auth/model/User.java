package com.ecommerce.auth.model;

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
 * JPA entity backing the {@code users} table. Deliberately kept separate
 * from every request/response DTO (see the {@code dto} package) so the
 * password hash - and any other persistence-only detail - can never leak
 * across the API boundary by accident.
 *
 * <p>Implements {@link Persistable} because {@code id} is a client-assigned
 * UUID (not {@code @GeneratedValue}): without this, Spring Data JPA's
 * default {@code isNew()} heuristic sees a non-null id at save() time and
 * treats every create as an update, routing {@code save()} through
 * {@code merge()} instead of {@code persist()}. {@code merge()} defers the
 * actual INSERT to flush/commit time - *after* {@link
 * com.ecommerce.auth.service.AuthService#register} has already returned -
 * so a concurrent duplicate-email race's unique-constraint violation would
 * escape that method's try/catch entirely and surface as an unhandled 500
 * instead of the intended 409. The {@code isNew} flag here forces
 * {@code persist()} for a freshly constructed instance, so the INSERT (and
 * any constraint violation it throws) happens synchronously, inside the
 * try/catch that expects it.</p>
 */
@Entity
@Table(name = "users")
public class User implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected User() {
        // JPA
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        // Set eagerly (rather than via Hibernate's @CreationTimestamp) so the
        // value is already on this in-memory instance immediately after
        // save() - @CreationTimestamp only populates the field at flush
        // time, which is too late for AuthService.register to return it.
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
