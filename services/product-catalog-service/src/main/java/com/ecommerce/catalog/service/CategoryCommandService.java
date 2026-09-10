package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.CategoryCreateRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.exception.DuplicateCategoryNameException;
import com.ecommerce.catalog.model.Category;
import com.ecommerce.catalog.repository.CategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS-lite write side for categories. Owns the one business rule (names
 * are unique, case-insensitively) and evicts the {@code categories} read
 * cache on every mutation - see {@link CategoryQueryService} for the read
 * side and {@code CacheConfig} for the caching rationale.
 */
@Service
public class CategoryCommandService {

    private final CategoryRepository categoryRepository;

    public CategoryCommandService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryNameException(name);
        }

        Category category = new Category(name, request.description());
        try {
            return toResponse(categoryRepository.save(category));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // A concurrent request won the race between our existsByNameIgnoreCase
            // check and this save() - the unique constraint on name caught it;
            // report it the same way.
            throw new DuplicateCategoryNameException(name);
        }
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.getCreatedAt());
    }
}
