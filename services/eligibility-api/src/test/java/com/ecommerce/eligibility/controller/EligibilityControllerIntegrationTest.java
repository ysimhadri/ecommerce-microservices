package com.ecommerce.eligibility.controller;

import com.ecommerce.eligibility.AbstractIntegrationTest;
import com.ecommerce.eligibility.model.EligibilityStatus;
import com.ecommerce.eligibility.repository.CustomerEligibilityHistoryRepository;
import com.ecommerce.eligibility.repository.EligibilityAuditLogRepository;
import com.ecommerce.eligibility.repository.EligibilityDecisionRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityControllerIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EligibilityDecisionRepository decisionRepository;

    @Autowired
    private CustomerEligibilityHistoryRepository historyRepository;

    @Autowired
    private EligibilityAuditLogRepository auditLogRepository;

    private String url(String customerId, String productType) {
        return "http://localhost:" + port + "/api/eligibility/" + customerId + "?productType=" + productType;
    }

    @Test
    @SuppressWarnings("unchecked")
    void eligibleCreditCard_returns200AndPersistsDecisionAndHistory() {
        String customerId = "high-score-" + UUID.randomUUID();

        ResponseEntity<Map> response = restTemplate.getForEntity(url(customerId, "CREDIT_CARD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(EligibilityStatus.ELIGIBLE.name());
        assertThat(response.getBody().get("customerId")).isEqualTo(customerId);
        assertThat(response.getBody().get("creditScore")).isEqualTo(790);

        assertThat(decisionRepository.findAll())
                .anyMatch(d -> customerId.equals(d.getCustomerId()) && d.getStatus() == EligibilityStatus.ELIGIBLE);
        assertThat(historyRepository.findByCustomerIdAndProductType(
                customerId, com.ecommerce.eligibility.model.ProductType.CREDIT_CARD))
                .isPresent()
                .get()
                .extracting(h -> h.getLastStatus())
                .isEqualTo(EligibilityStatus.ELIGIBLE);

        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(
                () -> assertThat(auditLogRepository.countByCustomerId(customerId)).isGreaterThan(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitBreakerFallback_returnsManualReviewRequired() {
        String customerId = "force-fail-" + UUID.randomUUID();

        ResponseEntity<Map> response = restTemplate.getForEntity(url(customerId, "CREDIT_CARD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(EligibilityStatus.MANUAL_REVIEW_REQUIRED.name());
        assertThat(String.valueOf(response.getBody().get("reason")).toLowerCase())
                .contains("manual review required");
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingProductType_returns400() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/eligibility/cust-1", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownProductType_returns400() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("cust-1", "WIDGET"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownCustomer_returns404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("unknown", "CREDIT_CARD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void actuatorHealthAndPrometheus_areExposed() {
        ResponseEntity<Map> health = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", Map.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isNotNull();
        assertThat(String.valueOf(health.getBody().get("status"))).isIn("UP", "DEGRADED");

        ResponseEntity<String> prometheus = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus", String.class);
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prometheus.getBody()).contains("jvm_");
    }
}
