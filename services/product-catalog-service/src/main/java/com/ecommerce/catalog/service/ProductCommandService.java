package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.ProductCreateRequest;
import com.ecommerce.catalog.dto.ProductResponse;
import com.ecommerce.catalog.exception.InvalidCategoryReferenceException;
import com.ecommerce.catalog.model.Category;
import com.ecommerce.catalog.model.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS-lite write side for products. Products are not written into any
 * cache here - {@link ProductQueryService} populates the {@code products}
 * cache lazily on first read of a given id, so a newly created product is
 * simply a cache miss the first time it's fetched, never stale data.
 */
@Service
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductCommandService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new InvalidCategoryReferenceException(request.categoryId()));

        Product product = new Product(request.name().trim(), request.description(), request.price(), category.getId());
        Product saved = productRepository.save(product);
        return toResponse(saved, category.getName());
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
