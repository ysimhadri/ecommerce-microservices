package com.ecommerce.eligibility.service;

import com.ecommerce.eligibility.AbstractIntegrationTest;
import com.ecommerce.eligibility.model.EligibilityStatus;
import com.ecommerce.eligibility.model.ProductType;
import com.ecommerce.eligibility.repository.CustomerEligibilityHistoryRepository;
import com.ecommerce.eligibility.repository.EligibilityDecisionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EligibilityPersistenceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EligibilityPersistenceService persistenceService;

    @Autowired
    private EligibilityDecisionRepository decisionRepository;

    @SpyBean
    private CustomerEligibilityHistoryRepository historyRepository;

    @AfterEach
    void resetHistorySpy() {
        reset(historyRepository);
    }

    @Test
    void record_writesDecisionAndHistoryTogether() {
        String customerId = "persist-ok";

        persistenceService.record(customerId, ProductType.CREDIT_CARD, EligibilityStatus.ELIGIBLE, "ok", 720);

        assertThat(decisionRepository.findAll())
                .anyMatch(d -> customerId.equals(d.getCustomerId()));
        assertThat(historyRepository.findByCustomerIdAndProductType(customerId, ProductType.CREDIT_CARD))
                .isPresent();
    }

    @Test
    void record_rollsBackDecisionWhenHistorySaveFails() {
        String customerId = "persist-rollback";
        doThrow(new RuntimeException("history write failed")).when(historyRepository).saveAndFlush(any());

        assertThatThrownBy(() -> persistenceService.record(
                customerId, ProductType.MORTGAGE, EligibilityStatus.ELIGIBLE, "ok", 800))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("history write failed");

        assertThat(decisionRepository.findAll())
                .noneMatch(d -> customerId.equals(d.getCustomerId()));
        assertThat(historyRepository.findByCustomerIdAndProductType(customerId, ProductType.MORTGAGE))
                .isEmpty();
    }
}
