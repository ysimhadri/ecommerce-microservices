package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.CategoryCreateRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.dto.ProductCreateRequest;
import com.ecommerce.catalog.dto.ProductResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed integration test exercising the real HTTP endpoints
 * end-to-end against a real PostgreSQL instance (Flyway migrations,
 * including the pg_trgm search indexes, run for real).
 *
 * <p>Write endpoints now require a service-to-service JWT with the
 * {@code catalog:write} scope and an audience matching this service (see
 * SecurityConfig/PortalJwtValidator) - {@link #mintToken} mints one
 * directly with jjwt using this service's own configured secret/audience,
 * the same duplication PortalJwtValidator itself documents, rather than
 * standing up a real api-portal-service for this test.
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

    @Value("${app.portal.jwt.secret}")
    private String portalJwtSecret;

    @Value("${app.portal.jwt.audience}")
    private String portalJwtAudience;

    /**
     * See auth-service's equivalent test for why: the JDK's default
     * HttpURLConnection-backed request factory can mishandle retrying a
     * request body after certain error responses - Apache HttpClient does
     * not have this issue. Several tests here deliberately provoke a
     * 401/403 on the write endpoints.
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

    /** A valid S2S token for this service, carrying whichever scopes the test needs (usually just catalog:write). */
    private String mintToken(String... scopes) {
        SecretKey signingKey = Keys.hmacShaKeyFor(portalJwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("api-portal")
                .subject("test-consumer-service")
                .audience().add(portalJwtAudience).and()
                .claim("scope", String.join(" ", scopes))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private String writeToken() {
        return mintToken("catalog:write");
    }

    private <T> ResponseEntity<T> postAuthed(String url, Object body, Class<T> responseType, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private UUID createCategory(String name) {
        ResponseEntity<CategoryResponse> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest(name, "test category"), CategoryResponse.class, writeToken());
        return response.getBody().id();
    }

    // --- Category create ---------------------------------------------

    @Test
    void createCategory_withValidWriteToken_returns201() {
        ResponseEntity<CategoryResponse> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest(uniqueName("Electronics"), "Gadgets"),
                CategoryResponse.class, writeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void createCategory_withoutToken_returns401() {
        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest(uniqueName("NoToken"), null), Map.class, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void createCategory_withTokenMissingWriteScope_returns403() {
        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest(uniqueName("WrongScope"), null),
                Map.class, mintToken("catalog:read"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
    }

    @Test
    void createCategory_withTokenForWrongAudience_returns401() {
        SecretKey signingKey = Keys.hmacShaKeyFor(portalJwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String wrongAudienceToken = Jwts.builder()
                .issuer("api-portal")
                .subject("test-consumer-service")
                .audience().add("some-other-service").and()
                .claim("scope", "catalog:write")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest(uniqueName("WrongAudience"), null),
                Map.class, wrongAudienceToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createCategory_withDuplicateName_returns409() {
        String name = uniqueName("Books");
        postAuthed(baseUrl() + "/categories", new CategoryCreateRequest(name, null), CategoryResponse.class, writeToken());

        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest(name, null), Map.class, writeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CATEGORY_ALREADY_EXISTS");
    }

    @Test
    void createCategory_withBlankName_returns400WithValidationError() {
        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/categories", new CategoryCreateRequest("", null), Map.class, writeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // --- Category list (public, no token needed) ---------------------------------------------

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

        ResponseEntity<ProductResponse> response = postAuthed(
                baseUrl() + "/products",
                new ProductCreateRequest("Headphones", "Noise-cancelling over-ear", new BigDecimal("149.99"), categoryId),
                ProductResponse.class, writeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().categoryName()).isEqualTo(categoryName);
        assertThat(response.getBody().price()).isEqualByComparingTo("149.99");
    }

    @Test
    void createProduct_withoutToken_returns401() {
        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/products",
                new ProductCreateRequest("Headphones", null, new BigDecimal("149.99"), UUID.randomUUID()),
                Map.class, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createProduct_withUnknownCategory_returns400() {
        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/products",
                new ProductCreateRequest("Headphones", null, new BigDecimal("149.99"), UUID.randomUUID()),
                Map.class, writeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_CATEGORY_ID");
    }

    @Test
    void createProduct_withNegativePrice_returns400WithValidationError() {
        UUID categoryId = createCategory(uniqueName("Misc"));

        ResponseEntity<Map> response = postAuthed(
                baseUrl() + "/products",
                new ProductCreateRequest("Broken", null, new BigDecimal("-1.00"), categoryId),
                Map.class, writeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // --- Product get by id (public, no token needed) ---------------------------------------------

    @Test
    void getProductById_withKnownId_returns200() {
        UUID categoryId = createCategory(uniqueName("Kitchen"));
        ProductResponse created = postAuthed(
                baseUrl() + "/products", new ProductCreateRequest("Blender", null, new BigDecimal("39.99"), categoryId),
                ProductResponse.class, writeToken()).getBody();

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

    // --- Product list/filter/search (public, no token needed) ---------------------------------------------

    @Test
    void listProducts_filteredByCategory_returnsOnlyThatCategorysProducts() {
        UUID categoryAId = createCategory(uniqueName("CategoryA"));
        UUID categoryBId = createCategory(uniqueName("CategoryB"));
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest("Widget A", null, BigDecimal.TEN, categoryAId), ProductResponse.class, writeToken());
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest("Widget B", null, BigDecimal.TEN, categoryBId), ProductResponse.class, writeToken());

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/products?categoryId=" + categoryAId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).extracting(p -> p.get("name")).containsExactly("Widget A");
    }

    @Test
    void searchProducts_byNameTerm_returnsMatchingProductsOnly() {
        UUID categoryId = createCategory(uniqueName("Search"));
        String uniqueToken = "Zylophone" + UUID.randomUUID().toString().substring(0, 8);
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest(uniqueToken, "a musical instrument", BigDecimal.TEN, categoryId), ProductResponse.class, writeToken());
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest("Unrelated Gadget", "does not match", BigDecimal.TEN, categoryId), ProductResponse.class, writeToken());

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/products?q=" + uniqueToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("name")).isEqualTo(uniqueToken);
    }

    @Test
    void searchProducts_byCategoryAndTerm_returnsOnlyProductsMatchingBothFilters() {
        // Real Postgres, not a mock: proves ProductRepository.searchByCategoryIdAndTerm's
        // native SQL actually runs correctly - a mocked unit test can't catch a broken
        // AND/OR or a binding mismatch in the query text itself.
        UUID categoryAId = createCategory(uniqueName("CombinedA"));
        UUID categoryBId = createCategory(uniqueName("CombinedB"));
        String uniqueToken = "Widgetron" + UUID.randomUUID().toString().substring(0, 8);

        // Matches both filters - should be returned.
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest(uniqueToken, null, BigDecimal.TEN, categoryAId), ProductResponse.class, writeToken());
        // Matches the term but the wrong category - must be excluded.
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest(uniqueToken, null, BigDecimal.TEN, categoryBId), ProductResponse.class, writeToken());
        // Matches the category but not the term - must be excluded.
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest("Unrelated Gadget", null, BigDecimal.TEN, categoryAId), ProductResponse.class, writeToken());

        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/products?categoryId=" + categoryAId + "&q=" + uniqueToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("name")).isEqualTo(uniqueToken);
    }

    @Test
    void searchProducts_byTermContainingLikeWildcards_treatsThemAsLiteralCharacters() {
        UUID categoryId = createCategory(uniqueName("Wildcard"));
        String literalToken = "100%_off-" + UUID.randomUUID().toString().substring(0, 8);
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest(literalToken, null, BigDecimal.TEN, categoryId), ProductResponse.class, writeToken());
        // Would also match "100%_off..." under an unescaped ILIKE '%100X_off%' pattern
        // (any single character for '_', anything for '%') - must NOT be returned once
        // '%' and '_' are escaped to their literal meaning.
        postAuthed(baseUrl() + "/products",
                new ProductCreateRequest("100Xoff-decoy", null, BigDecimal.TEN, categoryId), ProductResponse.class, writeToken());

        // literalToken contains a raw '%' - build the URI properly rather than string-
        // concatenating it into the URL, since '%' is a reserved URI escape character.
        URI uri = UriComponentsBuilder.fromUriString(baseUrl() + "/products")
                .queryParam("q", literalToken)
                .build()
                .encode()
                .toUri();
        ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("name")).isEqualTo(literalToken);
    }
}
