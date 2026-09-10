package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductCreateRequest;
import com.ecommerce.catalog.dto.ProductResponse;
import com.ecommerce.catalog.service.ProductCommandService;
import com.ecommerce.catalog.service.ProductQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Layered architecture, API tier: thin - validates input shape via
 * {@code @Valid} and delegates every business decision to the command/query
 * services. Versioned under {@code /api/v1/catalog} per the repo's API
 * convention. v1 has no auth: all endpoints are open (see README).
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    public ProductController(ProductCommandService productCommandService, ProductQueryService productQueryService) {
        this.productCommandService = productCommandService;
        this.productQueryService = productQueryService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse created = productCommandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(productQueryService.getById(id));
    }

    /**
     * Filters by category, does a name/description text search, or both -
     * whichever query params are present. With neither, returns every
     * product page by page.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productQueryService.list(categoryId, q, pageable));
    }
}
