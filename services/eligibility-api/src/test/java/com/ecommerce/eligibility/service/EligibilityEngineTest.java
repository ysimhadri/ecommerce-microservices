package com.ecommerce.eligibility.service;

import com.ecommerce.eligibility.dto.CreditBureauReport;
import com.ecommerce.eligibility.dto.CustomerProfileResponse;
import com.ecommerce.eligibility.model.EligibilityStatus;
import com.ecommerce.eligibility.model.ProductType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEngineTest {

    private final EligibilityEngine engine = new EligibilityEngine(null, null, null);

    private static CustomerProfileResponse profile(String kyc) {
        return new CustomerProfileResponse("c", "Test", kyc, null);
    }

    private static CreditBureauReport report(int score) {
        return CreditBureauReport.scored("c", score);
    }

    private EligibilityStatus status(ProductType type, int score) {
        return engine.decide(profile("VERIFIED"), report(score), type).status();
    }

    @Test
    void thresholdsAreInclusivePerProduct() {
        assertThat(status(ProductType.CREDIT_CARD, 650)).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(status(ProductType.CREDIT_CARD, 649)).isEqualTo(EligibilityStatus.INELIGIBLE);
        assertThat(status(ProductType.PERSONAL_LOAN, 700)).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(status(ProductType.PERSONAL_LOAN, 699)).isEqualTo(EligibilityStatus.INELIGIBLE);
        assertThat(status(ProductType.MORTGAGE, 740)).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(status(ProductType.MORTGAGE, 739)).isEqualTo(EligibilityStatus.INELIGIBLE);
    }

    @Test
    void unverifiedKycIsIneligibleEvenWithHighScore() {
        assertThat(engine.decide(profile("PENDING"), report(800), ProductType.CREDIT_CARD).status())
                .isEqualTo(EligibilityStatus.INELIGIBLE);
    }
}
