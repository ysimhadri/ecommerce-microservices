package com.ecommerce.eligibility.controller;

import com.ecommerce.eligibility.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "resilience4j.ratelimiter.instances.eligibility.limitForPeriod=3",
        "resilience4j.ratelimiter.instances.eligibility.limitRefreshPeriod=30s",
        "resilience4j.ratelimiter.instances.eligibility.timeoutDuration=0s"
})
class EligibilityRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void excessRequests_return429() {
        String customerId = "rate-" + UUID.randomUUID();
        String url = "http://localhost:" + port + "/api/eligibility/" + customerId + "?productType=CREDIT_CARD";

        List<HttpStatus> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            statuses.add(HttpStatus.valueOf(response.getStatusCode().value()));
            if (response.getStatusCode().value() == 429) {
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().get("code")).isEqualTo("RATE_LIMIT_EXCEEDED");
            }
        }

        assertThat(statuses).contains(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(statuses.stream().filter(HttpStatus.OK::equals).count()).isGreaterThanOrEqualTo(1);
    }
}
