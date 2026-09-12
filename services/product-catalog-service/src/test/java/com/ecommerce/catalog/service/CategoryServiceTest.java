package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.CategoryCreateRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.exception.DuplicateCategoryNameException;
import com.ecommerce.catalog.model.Category;
import com.ecommerce.catalog.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the category command/query services (create and list),
 * with persistence mocked out. Caching itself is Spring's own
 * well-tested machinery, so these exercise the business logic the
 * {@code @Cacheable}/{@code @CacheEvict} annotations wrap, not the cache.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryCommandService categoryCommandService;

    @InjectMocks
    private CategoryQueryService categoryQueryService;

    private static final String NAME = "Electronics";

    @Test
    void create_withNewName_persistsAndReturnsCategory() {
        when(categoryRepository.existsByNameIgnoreCase(NAME)).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryCommandService.create(new CategoryCreateRequest(NAME, "Gadgets and devices"));

        assertThat(response.name()).isEqualTo(NAME);
        assertThat(response.description()).isEqualTo("Gadgets and devices");
        verify(categoryRepository).saveAndFlush(any(Category.class));
    }

    @Test
    void create_withDuplicateName_throwsAndNeverPersists() {
        when(categoryRepository.existsByNameIgnoreCase(NAME)).thenReturn(true);

        assertThatThrownBy(() -> categoryCommandService.create(new CategoryCreateRequest(NAME, null)))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void create_trimsName() {
        when(categoryRepository.existsByNameIgnoreCase(NAME)).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryCommandService.create(new CategoryCreateRequest("  " + NAME + "  ", null));

        assertThat(response.name()).isEqualTo(NAME);
    }

    @Test
    void listAll_returnsEveryCategory() {
        Category electronics = new Category("Electronics", null);
        Category books = new Category("Books", null);
        when(categoryRepository.findAll()).thenReturn(List.of(electronics, books));

        List<CategoryResponse> categories = categoryQueryService.listAll();

        assertThat(categories).extracting(CategoryResponse::name).containsExactly("Electronics", "Books");
    }
}
