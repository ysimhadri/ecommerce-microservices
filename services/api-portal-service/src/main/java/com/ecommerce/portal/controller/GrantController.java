package com.ecommerce.portal.controller;

import com.ecommerce.portal.dto.GrantCreateRequest;
import com.ecommerce.portal.dto.GrantResponse;
import com.ecommerce.portal.service.GrantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Layered architecture, API tier: thin - delegates every business decision
 * to {@link GrantService}. Same v1 "admin API is open" scope cut as
 * {@link ServiceRegistryController} - see README.
 */
@RestController
@RequestMapping("/api/v1/portal/grants")
public class GrantController {

    private final GrantService grantService;

    public GrantController(GrantService grantService) {
        this.grantService = grantService;
    }

    @PostMapping
    public ResponseEntity<GrantResponse> create(@Valid @RequestBody GrantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(grantService.createGrant(request));
    }

    @GetMapping
    public ResponseEntity<List<GrantResponse>> list(
            @RequestParam(required = false) UUID consumerServiceId,
            @RequestParam(required = false) UUID producerServiceId) {
        return ResponseEntity.ok(grantService.list(consumerServiceId, producerServiceId));
    }
}
