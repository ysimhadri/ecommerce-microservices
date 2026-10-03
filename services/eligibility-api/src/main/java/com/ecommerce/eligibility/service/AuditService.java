package com.ecommerce.eligibility.service;

import com.ecommerce.eligibility.model.EligibilityAuditLog;
import com.ecommerce.eligibility.model.ProductType;
import com.ecommerce.eligibility.repository.EligibilityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fire-and-forget audit of every eligibility check. Runs on {@code auditExecutor}.
 * Persistence uses the repository's own transaction so {@code @Async} does not
 * skip a class-level {@code @Transactional} proxy.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final EligibilityAuditLogRepository auditLogRepository;

    public AuditService(EligibilityAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async("auditExecutor")
    public void record(String customerId, ProductType productType, String outcome) {
        try {
            auditLogRepository.save(new EligibilityAuditLog(customerId, productType, outcome));
        } catch (RuntimeException ex) {
            log.error("Failed to persist eligibility audit for customer {}", customerId, ex);
        }
    }
}
