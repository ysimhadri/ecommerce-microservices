package com.ecommerce.eligibility.service;

import com.ecommerce.eligibility.client.CreditBureauClient;
import com.ecommerce.eligibility.client.CustomerProfileClient;
import com.ecommerce.eligibility.dto.CreditBureauReport;
import com.ecommerce.eligibility.dto.CustomerProfileResponse;
import com.ecommerce.eligibility.dto.EligibilityResponse;
import com.ecommerce.eligibility.model.EligibilityStatus;
import com.ecommerce.eligibility.model.ProductType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Cached evaluation path. A cache hit skips the bureau, profile, and
 * transactional persist. Manual-review results are not cached so a recovered
 * bureau is visible before TTL.
 */
@Service
public class EligibilityEngine {

    private final CustomerProfileClient customerProfileClient;
    private final CreditBureauClient creditBureauClient;
    private final EligibilityPersistenceService persistenceService;

    public EligibilityEngine(
            CustomerProfileClient customerProfileClient,
            CreditBureauClient creditBureauClient,
            EligibilityPersistenceService persistenceService) {
        this.customerProfileClient = customerProfileClient;
        this.creditBureauClient = creditBureauClient;
        this.persistenceService = persistenceService;
    }

    @Cacheable(
            cacheNames = "eligibility",
            key = "#customerId + ':' + #productType",
            unless = "#result.manualReview()")
    public EligibilityResponse evaluate(String customerId, ProductType productType) {
        CustomerProfileResponse profile = customerProfileClient.getProfile(customerId);
        CreditBureauReport report = creditBureauClient.getReport(customerId);
        Decision decision = decide(profile, report, productType);

        persistenceService.record(customerId, productType, decision.status(), decision.reason(), decision.creditScore());

        return new EligibilityResponse(
                customerId,
                productType,
                decision.status(),
                decision.reason(),
                decision.creditScore(),
                Instant.now());
    }

    Decision decide(CustomerProfileResponse profile, CreditBureauReport report, ProductType productType) {
        if (!report.available()) {
            String reason = report.detail() != null && !report.detail().isBlank()
                    ? report.detail()
                    : "manual review required";
            if (!reason.toLowerCase().contains("manual review required")) {
                reason = "manual review required: " + reason;
            }
            return new Decision(EligibilityStatus.MANUAL_REVIEW_REQUIRED, reason, report.score());
        }

        if (profile.kycStatus() == null || !"VERIFIED".equalsIgnoreCase(profile.kycStatus())) {
            return new Decision(
                    EligibilityStatus.INELIGIBLE,
                    "KYC is not verified",
                    report.score());
        }

        int threshold = thresholdFor(productType);
        int score = report.score() == null ? 0 : report.score();
        if (score >= threshold) {
            return new Decision(
                    EligibilityStatus.ELIGIBLE,
                    "Meets credit and KYC requirements for " + productType,
                    score);
        }
        return new Decision(
                EligibilityStatus.INELIGIBLE,
                "Credit score below threshold (" + threshold + ") for " + productType,
                score);
    }

    private static int thresholdFor(ProductType productType) {
        return switch (productType) {
            case CREDIT_CARD -> 650;
            case PERSONAL_LOAN -> 700;
            case MORTGAGE -> 740;
        };
    }

    record Decision(EligibilityStatus status, String reason, Integer creditScore) {
    }
}
