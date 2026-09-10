package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.CategoryCreateRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.service.CategoryCommandService;
import com.ecommerce.catalog.service.CategoryQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Layered architecture, API tier: thin - validates input shape via
 * {@code @Valid} and delegates every business decision to the command/query
 * services. Versioned under {@code /api/v1/catalog} per the repo's API
 * convention. v1 has no auth: both endpoints are open (see README).
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CategoryController {

    private final CategoryCommandService categoryCommandService;
    private final CategoryQueryService categoryQueryService;

    public CategoryController(CategoryCommandService categoryCommandService, CategoryQueryService categoryQueryService) {
        this.categoryCommandService = categoryCommandService;
        this.categoryQueryService = categoryQueryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse created = categoryCommandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list() {
        return ResponseEntity.ok(categoryQueryService.listAll());
    }
}
