package com.ecommerce.eligibility.service;

import com.ecommerce.eligibility.dto.EligibilityResponse;
import com.ecommerce.eligibility.model.ProductType;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.stereotype.Service;

/**
 * Public eligibility entry point. Rate-limited at ~50 requests/sec. Audit is
 * always scheduled (including cache hits); the cached compute lives on
 * {@link EligibilityEngine}.
 */
@Service
public class EligibilityService {

    private final EligibilityEngine eligibilityEngine;
    private final AuditService auditService;

    public EligibilityService(EligibilityEngine eligibilityEngine, AuditService auditService) {
        this.eligibilityEngine = eligibilityEngine;
        this.auditService = auditService;
    }

    @RateLimiter(name = "eligibility")
    public EligibilityResponse check(String customerId, ProductType productType) {
        EligibilityResponse response = eligibilityEngine.evaluate(customerId, productType);
        auditService.record(customerId, productType, response.status().name());
        return response;
    }
}
