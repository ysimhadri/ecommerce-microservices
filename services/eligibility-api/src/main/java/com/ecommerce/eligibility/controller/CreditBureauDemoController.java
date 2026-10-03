package com.ecommerce.eligibility.controller;

import com.ecommerce.eligibility.client.CreditBureauClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Demo-only toggle for the force-failable mock credit bureau. Also reachable
 * without this endpoint by using a customerId that starts with {@code force-fail}.
 */
@RestController
@RequestMapping("/api/eligibility/demo")
public class CreditBureauDemoController {

    private final CreditBureauClient creditBureauClient;

    public CreditBureauDemoController(CreditBureauClient creditBureauClient) {
        this.creditBureauClient = creditBureauClient;
    }

    @PostMapping("/credit-bureau/fail")
    public ResponseEntity<Map<String, Object>> setForceFail(@RequestParam boolean enabled) {
        creditBureauClient.setForceFail(enabled);
        return ResponseEntity.ok(Map.of(
                "forceFail", creditBureauClient.isForceFail(),
                "hint", enabled
                        ? "Subsequent eligibility checks fall back to manual review required"
                        : "Mock credit bureau is serving scores again"));
    }
}
