package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CreditBureauReport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.creditBureau.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.creditBureau.minimumNumberOfCalls=4",
        "resilience4j.circuitbreaker.instances.creditBureau.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.creditBureau.waitDurationInOpenState=30s"
})
class CreditBureauClientCircuitBreakerTest {

    @Autowired
    private CreditBureauClient creditBureauClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void fallback_returnsUnavailableWhenBureauForcedDown() {
        CreditBureauReport report = creditBureauClient.getReport("force-fail-cb-unit");

        assertThat(report.available()).isFalse();
        assertThat(report.detail().toLowerCase()).contains("manual review required");
    }

    @Test
    void repeatedFailures_openCircuitAndStillFallback() {
        creditBureauClient.setForceFail(true);
        try {
            for (int i = 0; i < 8; i++) {
                CreditBureauReport report = creditBureauClient.getReport("cb-open-" + i);
                assertThat(report.available()).isFalse();
            }
            CircuitBreaker.State state = circuitBreakerRegistry.circuitBreaker("creditBureau").getState();
            assertThat(state).isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.CLOSED);
            CreditBureauReport after = creditBureauClient.getReport("cb-open-after");
            assertThat(after.available()).isFalse();
            assertThat(after.detail().toLowerCase()).contains("manual review required");
        } finally {
            creditBureauClient.setForceFail(false);
            circuitBreakerRegistry.circuitBreaker("creditBureau").reset();
        }
    }
}
