package com.ecommerce.eligibility.service;

import com.ecommerce.eligibility.model.CustomerEligibilityHistory;
import com.ecommerce.eligibility.model.EligibilityDecision;
import com.ecommerce.eligibility.model.EligibilityStatus;
import com.ecommerce.eligibility.model.ProductType;
import com.ecommerce.eligibility.repository.CustomerEligibilityHistoryRepository;
import com.ecommerce.eligibility.repository.EligibilityDecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dual-write: a new decision row and the per-customer history row commit or
 * roll back together. Callers must go through this bean so {@code @Transactional}
 * is applied (no self-invocation).
 */
@Service
public class EligibilityPersistenceService {

    private final EligibilityDecisionRepository decisionRepository;
    private final CustomerEligibilityHistoryRepository historyRepository;

    public EligibilityPersistenceService(
            EligibilityDecisionRepository decisionRepository,
            CustomerEligibilityHistoryRepository historyRepository) {
        this.decisionRepository = decisionRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public EligibilityDecision record(
            String customerId,
            ProductType productType,
            EligibilityStatus status,
            String reason,
            Integer creditScore) {
        EligibilityDecision decision = decisionRepository.saveAndFlush(
                new EligibilityDecision(customerId, productType, status, reason, creditScore));

        historyRepository.findByCustomerIdAndProductType(customerId, productType)
                .ifPresentOrElse(
                        history -> history.recordCheck(status, reason, creditScore),
                        () -> historyRepository.saveAndFlush(
                                new CustomerEligibilityHistory(customerId, productType, status, reason, creditScore)));

        return decision;
    }
}
