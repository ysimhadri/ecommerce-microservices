package com.ecommerce.portal.controller;

import com.ecommerce.portal.dto.ApiDeclareRequest;
import com.ecommerce.portal.dto.ApiResponse;
import com.ecommerce.portal.dto.GrantCreateRequest;
import com.ecommerce.portal.dto.GrantResponse;
import com.ecommerce.portal.dto.RotateSecretResponse;
import com.ecommerce.portal.dto.ServiceCreatedResponse;
import com.ecommerce.portal.dto.ServiceRegisterRequest;
import com.ecommerce.portal.dto.ServiceResponse;
import com.ecommerce.portal.dto.TokenRequest;
import com.ecommerce.portal.dto.TokenResponse;
import com.ecommerce.portal.model.ServiceRole;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed integration test exercising the full happy path
 * end-to-end against a real PostgreSQL instance (Flyway migrations run for
 * real): register a producer and a consumer, declare a producer API, grant
 * the consumer access, mint a token, and confirm a bad secret is rejected.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /** See auth-service's equivalent test for why: several tests here deliberately provoke 401s/409s. */
    @BeforeEach
    void useApacheHttpClientRequestFactory() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/portal";
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ServiceCreatedResponse registerService(String name, ServiceRole role) {
        ResponseEntity<ServiceCreatedResponse> response = restTemplate.postForEntity(
                baseUrl() + "/services",
                new ServiceRegisterRequest(name, name + " display name", null, role),
                ServiceCreatedResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    // --- Full happy path ---------------------------------------------

    @Test
    void fullHappyPath_registerDeclareGrantToken_andRejectBadSecret() {
        String producerName = uniqueName("product-catalog-service");
        String consumerName = uniqueName("order-service");

        ServiceCreatedResponse producer = registerService(producerName, ServiceRole.PRODUCER);
        ServiceCreatedResponse consumer = registerService(consumerName, ServiceRole.CONSUMER);
        assertThat(producer.clientSecret()).isNotBlank();
        assertThat(consumer.clientSecret()).isNotBlank();

        // Declare a producer API requiring catalog:write.
        ResponseEntity<ApiResponse> apiResponse = restTemplate.postForEntity(
                baseUrl() + "/services/" + producer.id() + "/apis",
                new ApiDeclareRequest("POST", "/api/v1/catalog/products", List.of("catalog:write")),
                ApiResponse.class);
        assertThat(apiResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiResponse[]> apis = restTemplate.getForEntity(
                baseUrl() + "/services/" + producer.id() + "/apis", ApiResponse[].class);
        assertThat(apis.getBody()).extracting(ApiResponse::pathPattern).contains("/api/v1/catalog/products");

        // Grant the consumer access with catalog:write.
        ResponseEntity<GrantResponse> grantResponse = restTemplate.postForEntity(
                baseUrl() + "/grants",
                new GrantCreateRequest(consumer.id(), producer.id(), List.of("catalog:write")),
                GrantResponse.class);
        assertThat(grantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(grantResponse.getBody().scopes()).containsExactly("catalog:write");

        ResponseEntity<GrantResponse[]> grants = restTemplate.getForEntity(
                baseUrl() + "/grants?consumerServiceId=" + consumer.id(), GrantResponse[].class);
        assertThat(grants.getBody()).hasSize(1);

        // Mint a token.
        ResponseEntity<TokenResponse> tokenResponse = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), consumer.clientSecret(), producerName, null),
                TokenResponse.class);
        assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tokenResponse.getBody().accessToken().split("\\.")).hasSize(3);
        assertThat(tokenResponse.getBody().scope()).isEqualTo("catalog:write");
        assertThat(tokenResponse.getBody().tokenType()).isEqualTo("Bearer");

        // A wrong secret is rejected.
        ResponseEntity<Map> badSecretResponse = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), "definitely-wrong", producerName, null),
                Map.class);
        assertThat(badSecretResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(badSecretResponse.getBody().get("code")).isEqualTo("INVALID_CLIENT");
    }

    @Test
    void tokenRequest_withoutAGrant_returns403GrantNotFound() {
        String producerName = uniqueName("no-grant-producer");
        String consumerName = uniqueName("no-grant-consumer");
        registerService(producerName, ServiceRole.PRODUCER);
        ServiceCreatedResponse consumer = registerService(consumerName, ServiceRole.CONSUMER);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), consumer.clientSecret(), producerName, null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("GRANT_NOT_FOUND");
    }

    @Test
    void tokenRequest_withUnknownAudience_returns400() {
        ServiceCreatedResponse consumer = registerService(uniqueName("consumer-only"), ServiceRole.CONSUMER);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), consumer.clientSecret(), "no-such-service", null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("UNKNOWN_AUDIENCE");
    }

    @Test
    void tokenRequest_requestingScopeBeyondGrant_returns403ScopeNotGranted() {
        String producerName = uniqueName("scoped-producer");
        ServiceCreatedResponse producer = registerService(producerName, ServiceRole.PRODUCER);
        ServiceCreatedResponse consumer = registerService(uniqueName("scoped-consumer"), ServiceRole.CONSUMER);
        restTemplate.postForEntity(baseUrl() + "/grants",
                new GrantCreateRequest(consumer.id(), producer.id(), List.of("catalog:read")), GrantResponse.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), consumer.clientSecret(), producerName, List.of("catalog:write")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("SCOPE_NOT_GRANTED");
    }

    // --- Registration edge cases ---------------------------------------------

    @Test
    void register_withDuplicateName_returns409() {
        String name = uniqueName("dup-service");
        registerService(name, ServiceRole.BOTH);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/services",
                new ServiceRegisterRequest(name, "Duplicate", null, ServiceRole.BOTH),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("SERVICE_ALREADY_EXISTS");
    }

    @Test
    void getService_neverReturnsSecretOrHash() {
        ServiceCreatedResponse created = registerService(uniqueName("no-leak-service"), ServiceRole.BOTH);

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/services/" + created.id(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContainKeys("clientSecret", "clientSecretHash");
    }

    @Test
    void declareApi_onConsumerOnlyService_returns403() {
        ServiceCreatedResponse consumerOnly = registerService(uniqueName("consumer-cannot-declare"), ServiceRole.CONSUMER);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/services/" + consumerOnly.id() + "/apis",
                new ApiDeclareRequest("GET", "/api/v1/whatever", List.of("whatever:read")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("NOT_A_PRODUCER");
    }

    // --- Secret rotation ---------------------------------------------

    @Test
    void rotateSecret_invalidatesOldSecretAndActivatesNewOne() {
        String producerName = uniqueName("rotate-producer");
        ServiceCreatedResponse producer = registerService(producerName, ServiceRole.PRODUCER);
        ServiceCreatedResponse consumer = registerService(uniqueName("rotate-consumer"), ServiceRole.CONSUMER);
        restTemplate.postForEntity(baseUrl() + "/grants",
                new GrantCreateRequest(consumer.id(), producer.id(), List.of("catalog:write")), GrantResponse.class);

        ResponseEntity<RotateSecretResponse> rotateResponse = restTemplate.postForEntity(
                baseUrl() + "/services/" + consumer.id() + "/rotate-secret", null, RotateSecretResponse.class);
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newSecret = rotateResponse.getBody().clientSecret();
        assertThat(newSecret).isNotEqualTo(consumer.clientSecret());

        ResponseEntity<Map> oldSecretAttempt = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), consumer.clientSecret(), producerName, null),
                Map.class);
        assertThat(oldSecretAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<TokenResponse> newSecretAttempt = restTemplate.postForEntity(
                baseUrl() + "/oauth/token",
                new TokenRequest(consumer.clientId(), newSecret, producerName, null),
                TokenResponse.class);
        assertThat(newSecretAttempt.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listApis_onUnknownServiceId_returns404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/services/" + UUID.randomUUID() + "/apis", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("SERVICE_NOT_FOUND");
    }

    @Test
    void createGrant_forSamePairTwice_returns409OnTheSecondRealHttpCall() {
        // Real HTTP + real DB, not a mock: GrantServiceTest's duplicate-grant case only
        // ever stubs existsBy...=true, so it never exercises the actual save() path this
        // test does on its second call.
        ServiceCreatedResponse producer = registerService(uniqueName("dup-grant-producer"), ServiceRole.PRODUCER);
        ServiceCreatedResponse consumer = registerService(uniqueName("dup-grant-consumer"), ServiceRole.CONSUMER);
        GrantCreateRequest request = new GrantCreateRequest(consumer.id(), producer.id(), List.of("catalog:write"));
        restTemplate.postForEntity(baseUrl() + "/grants", request, GrantResponse.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/grants", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("GRANT_ALREADY_EXISTS");
    }

    // --- Listing ---------------------------------------------

    @Test
    void listServices_includesEveryRegisteredService() {
        String name = uniqueName("listed-service");
        registerService(name, ServiceRole.BOTH);

        ResponseEntity<ServiceResponse[]> response = restTemplate.getForEntity(baseUrl() + "/services", ServiceResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ServiceResponse::name).contains(name);
    }
}
