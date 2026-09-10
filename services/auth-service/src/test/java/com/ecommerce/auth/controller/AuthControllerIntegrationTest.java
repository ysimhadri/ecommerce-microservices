package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.security.JwtService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed integration test exercising the real HTTP endpoints
 * end-to-end against a real PostgreSQL instance (Flyway migrations run for
 * real). Covers all six rows of the spec's I/O & Edge-Case matrix.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /**
     * The JDK's default HttpURLConnection-backed request factory cannot
     * replay a POST body after a 401 response ("cannot retry due to server
     * authentication, in streaming mode") - several of our tests
     * deliberately provoke a 401 on POST /login, so swap in Apache
     * HttpClient, which handles this correctly.
     */
    @BeforeEach
    void useApacheHttpClientRequestFactory() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/auth";
    }

    private String uniqueEmail() {
        return "user-" + java.util.UUID.randomUUID() + "@example.com";
    }

    // --- Register success ---------------------------------------------

    @Test
    void register_withValidEmailAndPassword_returns201AndNeverEchoesPassword() {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "secret123");

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("email")).isEqualTo(email);
        assertThat(response.getBody()).doesNotContainKey("password");
        assertThat(response.getBody()).doesNotContainKey("passwordHash");
        assertThat(response.getBody().toString()).doesNotContain("secret123");
    }

    // --- Register duplicate email ---------------------------------------------

    @Test
    void register_withAlreadyRegisteredEmail_returns409WithJsonErrorBody() {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "secret123");
        restTemplate.postForEntity(baseUrl() + "/register", request, Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().toString()).doesNotContain("Exception").doesNotContain("\tat ");
    }

    // --- Login success ---------------------------------------------

    @Test
    void login_withCorrectCredentials_returns200AndJwtAccessToken() {
        String email = uniqueEmail();
        registerUser(email, "secret123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/login", new LoginRequest(email, "secret123"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        // A JWT has three dot-separated segments: header.payload.signature
        assertThat(response.getBody().accessToken().split("\\.")).hasSize(3);
    }

    // --- Login bad credentials ---------------------------------------------

    @Test
    void login_withWrongPassword_returns401WithGenericMessage() {
        String email = uniqueEmail();
        registerUser(email, "secret123");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/login", new LoginRequest(email, "wrong-password"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withUnknownEmail_returns401WithSameGenericMessageAsWrongPassword_noUserEnumeration() {
        ResponseEntity<Map> unknownEmailResponse = restTemplate.postForEntity(
                baseUrl() + "/login", new LoginRequest(uniqueEmail(), "secret123"), Map.class);

        String email = uniqueEmail();
        registerUser(email, "secret123");
        ResponseEntity<Map> wrongPasswordResponse = restTemplate.postForEntity(
                baseUrl() + "/login", new LoginRequest(email, "wrong-password"), Map.class);

        assertThat(unknownEmailResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmailResponse.getBody().get("message"))
                .isEqualTo(wrongPasswordResponse.getBody().get("message"));
    }

    // --- Get profile, valid token ---------------------------------------------

    @Test
    void getMe_withValidToken_returns200WithMatchingProfileAndNoPasswordHash() {
        String email = uniqueEmail();
        registerUser(email, "secret123");
        String token = login(email, "secret123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/me", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email")).isEqualTo(email);
        assertThat(response.getBody()).containsKeys("id", "email", "createdAt");
        assertThat(response.getBody().get("createdAt")).isNotNull();
        assertThat(response.getBody()).doesNotContainKey("passwordHash");
        assertThat(response.getBody()).doesNotContainKey("password");
    }

    // --- Get profile, missing/expired/invalid token ---------------------------------------------

    @Test
    void getMe_withNoAuthorizationHeader_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getMe_withMalformedToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this-is-not-a-valid-jwt");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/me", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getMe_withExpiredToken_returns401() {
        // A real, validly-signed token whose exp claim is already in the past -
        // distinct from the malformed-string case above, which never reaches
        // signature/expiry verification at all.
        JwtService expiredTokenIssuer = new JwtService(jwtSecret, -1_000L);
        String expiredToken = expiredTokenIssuer.generateToken(UUID.randomUUID(), "expired@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Bean validation rejection ---------------------------------------------

    @Test
    void register_withInvalidPayload_returns400WithValidationError() {
        RegisterRequest invalid = new RegisterRequest("not-an-email", "short");

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/register", invalid, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void login_withBlankPayload_returns400WithValidationError() {
        LoginRequest invalid = new LoginRequest("", "");

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/login", invalid, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // --- helpers ---------------------------------------------

    private UserProfileResponse registerUser(String email, String password) {
        return restTemplate.postForEntity(baseUrl() + "/register", new RegisterRequest(email, password), UserProfileResponse.class)
                .getBody();
    }

    private String login(String email, String password) {
        AuthResponse response = restTemplate.postForEntity(
                baseUrl() + "/login", new LoginRequest(email, password), AuthResponse.class).getBody();
        return response.accessToken();
    }
}
