package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductCreateRequest;
import com.ecommerce.catalog.dto.ProductResponse;
import com.ecommerce.catalog.exception.InvalidCategoryReferenceException;
import com.ecommerce.catalog.exception.ProductNotFoundException;
import com.ecommerce.catalog.model.Category;
import com.ecommerce.catalog.model.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the product command/query services: create
 * (success/unknown category), get-by-id (found/not found), and
 * list/filter/search, with persistence mocked out.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductCommandService productCommandService;

    @InjectMocks
    private ProductQueryService productQueryService;

    private static final String CATEGORY_NAME = "Electronics";

    @Test
    void create_withKnownCategory_persistsAndReturnsProductWithCategoryName() {
        Category category = new Category(CATEGORY_NAME, null);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productCommandService.create(
                new ProductCreateRequest("Headphones", "Noise-cancelling", new BigDecimal("99.99"), category.getId()));

        assertThat(response.name()).isEqualTo("Headphones");
        assertThat(response.categoryName()).isEqualTo(CATEGORY_NAME);
        assertThat(response.price()).isEqualByComparingTo("99.99");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void create_withUnknownCategory_throwsAndNeverPersists() {
        UUID unknownCategoryId = UUID.randomUUID();
        when(categoryRepository.findById(unknownCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCommandService.create(
                new ProductCreateRequest("Headphones", null, new BigDecimal("99.99"), unknownCategoryId)))
                .isInstanceOf(InvalidCategoryReferenceException.class);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getById_withKnownProduct_returnsProductWithCategoryName() {
        Category category = new Category(CATEGORY_NAME, null);
        Product product = new Product("Headphones", null, new BigDecimal("99.99"), category.getId());
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        ProductResponse response = productQueryService.getById(product.getId());

        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.categoryName()).isEqualTo(CATEGORY_NAME);
    }

    @Test
    void getById_withUnknownProduct_throwsProductNotFound() {
        UUID missingId = UUID.randomUUID();
        when(productRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productQueryService.getById(missingId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void list_withCategoryAndSearchTerm_delegatesToCombinedSearchAndResolvesCategoryNames() {
        Category category = new Category(CATEGORY_NAME, null);
        Product product = new Product("Wireless Headphones", "Bluetooth", new BigDecimal("49.99"), category.getId());
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.searchByCategoryIdAndTerm(category.getId(), "headphones", pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
        when(categoryRepository.findAllById(List.of(category.getId())))
                .thenReturn(List.of(category));

        PageResponse<ProductResponse> page = productQueryService.list(category.getId(), "headphones", pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).categoryName()).isEqualTo(CATEGORY_NAME);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void list_withNoFilters_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ProductResponse> page = productQueryService.list(null, null, pageable);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }
}
