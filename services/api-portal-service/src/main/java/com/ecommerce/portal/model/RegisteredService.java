package com.ecommerce.portal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * JPA entity backing the {@code registered_services} table - one row per
 * microservice known to the portal, whether it produces APIs, consumes
 * them, or both (see {@link ServiceRole}). Kept separate from every
 * request/response DTO (see the {@code dto} package) so the client secret
 * hash - and the plaintext secret, which never touches this entity at all -
 * can never leak across the API boundary by accident.
 *
 * <p>Implements {@link Persistable} for the same reason as {@code User}/
 * {@code Category}: a client-assigned UUID id defeats Spring Data JPA's
 * default {@code isNew()} heuristic, deferring the INSERT past
 * {@link ServiceRegistryService#register}'s try/catch. See those entities'
 * javadoc for the full explanation.</p>
 */
@Entity
@Table(name = "registered_services")
public class RegisteredService implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean isNew = true;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "base_url")
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceRole role;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false)
    private String clientSecretHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RegisteredService() {
        // JPA
    }

    public RegisteredService(String name, String displayName, String baseUrl, ServiceRole role,
                              String clientId, String clientSecretHash) {
        this.name = name;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.role = role;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.active = true;
        // Set eagerly (rather than via Hibernate's @CreationTimestamp) so the
        // value is already on this in-memory instance immediately after
        // save() - see Category/User for the same rationale.
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

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public ServiceRole getRole() {
        return role;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Rotate-secret: replace the hash in place, keep the same clientId. */
    public void setClientSecretHash(String clientSecretHash) {
        this.clientSecretHash = clientSecretHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegisteredService other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
