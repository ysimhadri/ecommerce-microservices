package com.ecommerce.eligibility.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Async audit trail of each eligibility HTTP check, including cache hits.
 * Written on a dedicated executor so the request thread is not blocked.
 */
@Entity
@Table(name = "eligibility_audit_log")
public class EligibilityAuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private ProductType productType;

    @Column(length = 32)
    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EligibilityAuditLog() {
        // JPA
    }

    public EligibilityAuditLog(String customerId, ProductType productType, String outcome) {
        this.customerId = customerId;
        this.productType = productType;
        this.outcome = outcome;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EligibilityAuditLog other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
