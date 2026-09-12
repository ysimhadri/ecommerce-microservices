package com.ecommerce.portal.controller;

import com.ecommerce.portal.dto.ApiDeclareRequest;
import com.ecommerce.portal.dto.ApiResponse;
import com.ecommerce.portal.dto.RotateSecretResponse;
import com.ecommerce.portal.dto.ServiceCreatedResponse;
import com.ecommerce.portal.dto.ServiceRegisterRequest;
import com.ecommerce.portal.dto.ServiceResponse;
import com.ecommerce.portal.service.ServiceRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Layered architecture, API tier: thin - validates input shape via
 * {@code @Valid} and delegates every business decision to
 * {@link ServiceRegistryService}. Versioned under {@code /api/v1/portal}
 * per the repo's API convention. v1's admin API is intentionally open - no
 * auth on these endpoints (see README) - registering a service is itself
 * how a caller obtains credentials, and there is no gateway yet to front
 * an operator-only surface.
 */
@RestController
@RequestMapping("/api/v1/portal/services")
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;

    public ServiceRegistryController(ServiceRegistryService serviceRegistryService) {
        this.serviceRegistryService = serviceRegistryService;
    }

    @PostMapping
    public ResponseEntity<ServiceCreatedResponse> register(@Valid @RequestBody ServiceRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceRegistryService.register(request));
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> list() {
        return ResponseEntity.ok(serviceRegistryService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceRegistryService.get(id));
    }

    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<RotateSecretResponse> rotateSecret(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceRegistryService.rotateSecret(id));
    }

    @PostMapping("/{id}/apis")
    public ResponseEntity<ApiResponse> declareApi(@PathVariable UUID id, @Valid @RequestBody ApiDeclareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceRegistryService.declareApi(id, request));
    }

    @GetMapping("/{id}/apis")
    public ResponseEntity<List<ApiResponse>> listApis(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceRegistryService.listApis(id));
    }
}
