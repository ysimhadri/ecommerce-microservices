package com.ecommerce.eligibility.repository;

import com.ecommerce.eligibility.model.EligibilityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EligibilityAuditLogRepository extends JpaRepository<EligibilityAuditLog, UUID> {

    long countByCustomerId(String customerId);
}
