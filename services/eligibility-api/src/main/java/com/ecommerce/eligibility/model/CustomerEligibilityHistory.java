package com.ecommerce.eligibility.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Latest eligibility outcome per customer+product. Updated in the same
 * {@code @Transactional} method that inserts {@link EligibilityDecision}.
 */
@Entity
@Table(
        name = "customer_eligibility_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_history_customer_product",
                columnNames = {"customer_id", "product_type"}))
public class CustomerEligibilityHistory {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", nullable = false, length = 32)
    private EligibilityStatus lastStatus;

    @Column(name = "last_reason", nullable = false, length = 512)
    private String lastReason;

    @Column(name = "last_credit_score")
    private Integer lastCreditScore;

    @Column(name = "check_count", nullable = false)
    private int checkCount;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    protected CustomerEligibilityHistory() {
        // JPA
    }

    public CustomerEligibilityHistory(
            String customerId,
            ProductType productType,
            EligibilityStatus status,
            String reason,
            Integer creditScore) {
        this.customerId = customerId;
        this.productType = productType;
        this.checkCount = 0;
        recordCheck(status, reason, creditScore);
    }

    public void recordCheck(EligibilityStatus status, String reason, Integer creditScore) {
        this.lastStatus = status;
        this.lastReason = reason;
        this.lastCreditScore = creditScore;
        this.lastCheckedAt = Instant.now();
        this.checkCount++;
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

    public EligibilityStatus getLastStatus() {
        return lastStatus;
    }

    public String getLastReason() {
        return lastReason;
    }

    public Integer getLastCreditScore() {
        return lastCreditScore;
    }

    public int getCheckCount() {
        return checkCount;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerEligibilityHistory other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
