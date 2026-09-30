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
 * Append-only record of a single eligibility evaluation. Written in the
 * same transaction as {@link CustomerEligibilityHistory}.
 */
@Entity
@Table(name = "eligibility_decisions")
public class EligibilityDecision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EligibilityStatus status;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EligibilityDecision() {
        // JPA
    }

    public EligibilityDecision(
            String customerId,
            ProductType productType,
            EligibilityStatus status,
            String reason,
            Integer creditScore) {
        this.customerId = customerId;
        this.productType = productType;
        this.status = status;
        this.reason = reason;
        this.creditScore = creditScore;
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

    public EligibilityStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EligibilityDecision other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
