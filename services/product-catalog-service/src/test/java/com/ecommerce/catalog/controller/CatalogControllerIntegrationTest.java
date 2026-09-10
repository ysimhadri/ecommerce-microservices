package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.CategoryCreateRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.dto.ProductCreateRequest;
import com.ecommerce.catalog.dto.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed integration test exercising the real HTTP endpoints
 * end-to-end against a real PostgreSQL instance (Flyway migrations,
 * including the pg_trgm search indexes, run for real).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * See auth-service's equivalent test for why: the JDK's default
     * HttpURLConnection-backed request factory can mishandle retrying a
     * request body after certain error responses - Apache HttpClient does
     * not have this issue.
     */
    @BeforeEach
    void useApacheHttpClientRequestFactory() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/catalog";
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private UUID createCategory(String name) {
        ResponseEntity<CategoryResponse> response = restTemplate.postForEntity(
                baseUrl() + "/categories", new CategoryCreateRequest(name, "test category"), CategoryResponse.class);
        return response.getBody().id();
    }

    // --- Category create ---------------------------------------------

    @Test
    void createCategory_withNewName_returns201() {
        ResponseEntity<CategoryResponse> response = restTemplate.postForEntity(
                baseUrl() + "/categories", new CategoryCreateRequest(uniqueName("Electronics"), "Gadgets"), CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void createCategory_withDuplicateName_returns409() {
        String name = uniqueName("Books");
        restTemplate.postForEntity(baseUrl() + "/categories", new CategoryCreateRequest(name, null), CategoryResponse.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/categories", new CategoryCreateRequest(name, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CATEGORY_ALREADY_EXISTS");
    }

    @Test
    void createCategory_withBlankName_returns400WithValidationError() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/categories", new CategoryCreateRequest("", null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // --- Category list ---------------------------------------------

    @Test
    void listCategories_includesEveryCreatedCategory() {
        String name = uniqueName("Toys");
        createCategory(name);

        ResponseEntity<CategoryResponse[]> response = restTemplate.getForEntity(baseUrl() + "/categories", CategoryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(CategoryResponse::name).contains(name);
    }

    // --- Product create ---------------------------------------------

    @Test
    void createProduct_withKnownCategory_returns201WithCategoryName() {
        String categoryName = uniqueName("Audio");
        UUID categoryId = createCategory(categoryName);

        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                baseUrl() + "/products",
                new ProductCreateRequest("Headphones", "Noise-cancelling over-ear", new BigDecimal("149.99"), categoryId),
                ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().categoryName()).isEqualTo(categoryName);
        assertThat(response.getBody().price()).isEqualByComparingTo("149.99");
    }

    @Test
    void createProduct_withUnknownCategory_returns400() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/products",
                new ProductCreateRequest("Headphones", null, new BigDecimal("149.99"), UUID.randomUUID()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_CATEGORY_ID");
    }

    @Test
    void createProduct_withNegativePrice_returns400WithValidationError() {
        UUID categoryId = createCategory(uniqueName("Misc"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/products",
                new ProductCreateRequest("Broken", null, new BigDecimal("-1.00"), categoryId),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // --- Product get by id ---------------------------------------------

    @Test
    void getProductById_withKnownId_returns200() {
        UUID categoryId = createCategory(uniqueName("Kitchen"));
        ProductResponse created = restTemplate.postForEntity(
                baseUrl() + "/products", new ProductCreateRequest("Blender", null, new BigDecimal("39.99"), categoryId),
                ProductResponse.class).getBody();

        ResponseEntity<ProductResponse> response = restTemplate.getForEntity(baseUrl() + "/products/" + created.id(), ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Blender");
    }

    @Test
    void getProductById_withUnknownId_returns404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/products/" + UUID.randomUUID(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("PRODUCT_NOT_FOUND");
    }

    // --- Product list/filter/search ---------------------------------------------

    @Test
    void listProducts_filteredByCategory_returnsOnlyThatCategorysProducts() {
        UUID categoryAId = createCategory(uniqueName("CategoryA"));
        UUID categoryBId = createCategory(uniqueName("CategoryB"));
        restTemplate.postForEntity(baseUrl() + "/products",
                new ProductCreateRequest("Widget A", null, BigDecimal.TEN, categoryAId), ProductResponse.class);
        restTemplate.postForEntity(baseUrl() + "/products",
                new ProductCreateRequest("Widget B", null, BigDecimal.TEN, categoryBId), ProductResponse.class);

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/products?categoryId=" + categoryAId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).extracting(p -> p.get("name")).containsExactly("Widget A");
    }

    @Test
    void searchProducts_byNameTerm_returnsMatchingProductsOnly() {
        UUID categoryId = createCategory(uniqueName("Search"));
        String uniqueToken = "Zylophone" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.postForEntity(baseUrl() + "/products",
                new ProductCreateRequest(uniqueToken, "a musical instrument", BigDecimal.TEN, categoryId), ProductResponse.class);
        restTemplate.postForEntity(baseUrl() + "/products",
                new ProductCreateRequest("Unrelated Gadget", "does not match", BigDecimal.TEN, categoryId), ProductResponse.class);

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/products?q=" + uniqueToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("name")).isEqualTo(uniqueToken);
    }
}
