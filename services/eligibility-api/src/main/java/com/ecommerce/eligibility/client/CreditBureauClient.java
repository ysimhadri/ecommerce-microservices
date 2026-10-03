package com.ecommerce.eligibility.client;

import com.ecommerce.eligibility.dto.CreditBureauReport;
import com.ecommerce.eligibility.exception.CreditBureauUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * External credit-bureau call, wrapped in a Resilience4j circuit breaker.
 * Default {@code mock} mode is force-failable for demos and tests; {@code http}
 * mode uses a plain (non-load-balanced) RestTemplate against
 * {@code app.credit-bureau.url}.
 */
@Component
public class CreditBureauClient {

    private static final Logger log = LoggerFactory.getLogger(CreditBureauClient.class);

    private final RestTemplate externalRestTemplate;
    private final String mode;
    private final String bureauUrl;
    private final AtomicBoolean forceFail;

    public CreditBureauClient(
            @Qualifier("externalRestTemplate") RestTemplate externalRestTemplate,
            @Value("${app.credit-bureau.mode:mock}") String mode,
            @Value("${app.credit-bureau.url:http://localhost:9090}") String bureauUrl,
            @Value("${app.credit-bureau.force-fail:false}") boolean forceFailAtStartup) {
        this.externalRestTemplate = externalRestTemplate;
        this.mode = mode;
        this.bureauUrl = bureauUrl;
        this.forceFail = new AtomicBoolean(forceFailAtStartup);
    }

    public void setForceFail(boolean enabled) {
        forceFail.set(enabled);
        log.warn("Credit bureau force-fail {}", enabled ? "ENABLED" : "disabled");
    }

    public boolean isForceFail() {
        return forceFail.get();
    }

    @CircuitBreaker(name = "creditBureau", fallbackMethod = "fallback")
    public CreditBureauReport getReport(String customerId) {
        if (forceFail.get() || customerId.toLowerCase().startsWith("force-fail")) {
            throw new CreditBureauUnavailableException(
                    "Credit bureau forced failure for customer " + customerId);
        }
        if ("http".equalsIgnoreCase(mode)) {
            return fetchRemote(customerId);
        }
        return mockReport(customerId);
    }

    /**
     * Invoked when the bureau throws or the circuit is open. The engine maps
     * {@code available=false} to "manual review required".
     */
    @SuppressWarnings("unused")
    public CreditBureauReport fallback(String customerId, Throwable throwable) {
        log.warn("Credit bureau fallback for customer {}: {}", customerId, throwable.toString());
        return CreditBureauReport.unavailable(
                customerId, "manual review required: credit bureau unavailable");
    }

    private CreditBureauReport fetchRemote(String customerId) {
        try {
            String url = bureauUrl.endsWith("/") ? bureauUrl + "score/{id}" : bureauUrl + "/score/{id}";
            CreditBureauReport report = externalRestTemplate.getForObject(url, CreditBureauReport.class, customerId);
            if (report == null) {
                throw new CreditBureauUnavailableException("Empty credit-bureau response");
            }
            return report;
        } catch (RestClientException ex) {
            throw new CreditBureauUnavailableException("Credit bureau HTTP call failed", ex);
        }
    }

    /**
     * Deterministic mock: high-score / low-score tokens, otherwise a passing 720.
     */
    private CreditBureauReport mockReport(String customerId) {
        String lower = customerId.toLowerCase();
        int score;
        if (lower.contains("low-score")) {
            score = 520;
        } else if (lower.contains("high-score")) {
            score = 790;
        } else {
            score = 720;
        }
        return CreditBureauReport.scored(customerId, score);
    }
}
