package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductResponse;
import com.ecommerce.catalog.exception.ProductNotFoundException;
import com.ecommerce.catalog.model.Category;
import com.ecommerce.catalog.model.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CQRS-lite read side for products: list/filter/search are read straight
 * from Postgres (too many distinct filter/page/search-term combinations to
 * cache usefully), while single-product lookups by id - the hot path for a
 * product detail page - are cached under the {@code products} cache name.
 * See {@code CacheConfig} for the eviction/staleness story.
 */
@Service
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductQueryService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Cacheable(cacheNames = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getName)
                .orElse(null);
        return toResponse(product, categoryName);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(UUID categoryId, String searchTerm, Pageable pageable) {
        String term = (searchTerm == null || searchTerm.isBlank()) ? null : escapeLikeWildcards(searchTerm.trim());

        Page<Product> page;
        if (term != null && categoryId != null) {
            page = productRepository.searchByCategoryIdAndTerm(categoryId, term, pageable);
        } else if (term != null) {
            page = productRepository.searchByTerm(term, pageable);
        } else if (categoryId != null) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }

        // Resolve every category name in one batch query rather than one
        // lookup per product row - avoids N+1 queries on the list/search path.
        Map<UUID, String> categoryNamesById = categoryRepository
                .findAllById(page.getContent().stream().map(Product::getCategoryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        Function<Product, ProductResponse> mapper = p -> toResponse(p, categoryNamesById.get(p.getCategoryId()));
        return PageResponse.from(page, mapper);
    }

    // ProductRepository's search queries build ILIKE CONCAT('%', :term, '%') patterns and
    // pair this with an ESCAPE '\' clause, so a literal % or _ typed by the caller is
    // matched literally instead of being treated as a wildcard.
    private String escapeLikeWildcards(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private ProductResponse toResponse(Product product, String categoryName) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategoryId(),
                categoryName,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
