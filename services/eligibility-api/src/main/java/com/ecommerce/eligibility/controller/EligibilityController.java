package com.ecommerce.eligibility.controller;

import com.ecommerce.eligibility.dto.EligibilityResponse;
import com.ecommerce.eligibility.model.ProductType;
import com.ecommerce.eligibility.service.EligibilityService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/eligibility/{customerId}?productType=CREDIT_CARD}
 */
@RestController
@RequestMapping("/api/eligibility")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    public EligibilityController(EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<EligibilityResponse> check(
            @PathVariable @NotBlank @Size(max = 64) String customerId,
            @RequestParam ProductType productType) {
        return ResponseEntity.ok(eligibilityService.check(customerId, productType));
    }
}
