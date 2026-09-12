package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.TokenRequest;
import com.ecommerce.portal.dto.TokenResponse;
import com.ecommerce.portal.exception.GrantNotFoundException;
import com.ecommerce.portal.exception.InvalidClientCredentialsException;
import com.ecommerce.portal.exception.NoRequestedScopesGrantedException;
import com.ecommerce.portal.exception.NotAConsumerException;
import com.ecommerce.portal.exception.UnknownAudienceException;
import com.ecommerce.portal.model.ConsumerGrant;
import com.ecommerce.portal.model.RegisteredService;
import com.ecommerce.portal.model.ServiceRole;
import com.ecommerce.portal.repository.ConsumerGrantRepository;
import com.ecommerce.portal.repository.RegisteredServiceRepository;
import com.ecommerce.portal.security.PortalJwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the client-credentials token flow, with persistence
 * mocked out but a real {@link PortalJwtService} and real
 * {@link BCryptPasswordEncoder} so the issued token's claims and the
 * secret check are exercised for real, not just mocked past.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String SECRET = "unit-test-only-hs256-signing-secret-at-least-32-bytes-long!!";
    private static final String RAW_SECRET = "correct-horse-battery-staple";

    @Mock
    private RegisteredServiceRepository registeredServiceRepository;

    @Mock
    private ConsumerGrantRepository consumerGrantRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private TokenService tokenService;

    private RegisteredService consumer;
    private RegisteredService producer;

    @BeforeEach
    void setUp() {
        PortalJwtService portalJwtService = new PortalJwtService(SECRET, 900_000L);
        tokenService = new TokenService(registeredServiceRepository, consumerGrantRepository, passwordEncoder, portalJwtService);

        consumer = new RegisteredService(
                "order-service", "Order Service", null, ServiceRole.CONSUMER,
                "client-order", passwordEncoder.encode(RAW_SECRET));
        producer = new RegisteredService(
                "product-catalog-service", "Product Catalog", null, ServiceRole.PRODUCER,
                "client-catalog", passwordEncoder.encode("irrelevant"));
    }

    private TokenRequest request(String clientSecret, String audience, List<String> scope) {
        return new TokenRequest("client-order", clientSecret, audience, scope);
    }

    @Test
    void issueToken_withValidCredentialsAndGrant_returnsTokenWithIntersectedScopesAndExpectedClaims() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(consumer));
        when(registeredServiceRepository.findByName("product-catalog-service")).thenReturn(Optional.of(producer));
        ConsumerGrant grant = new ConsumerGrant(consumer.getId(), producer.getId(), "catalog:write catalog:read");
        when(consumerGrantRepository.findByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId()))
                .thenReturn(Optional.of(grant));

        TokenResponse response = tokenService.issueToken(
                request(RAW_SECRET, "product-catalog-service", List.of("catalog:write")));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.scope()).isEqualTo("catalog:write");

        Claims claims = Jwts.parser().verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build().parseSignedClaims(response.accessToken()).getPayload();
        assertThat(claims.getIssuer()).isEqualTo("api-portal");
        assertThat(claims.getSubject()).isEqualTo("order-service");
        assertThat(claims.getAudience()).containsExactly("product-catalog-service");
        assertThat(claims.get("scope", String.class)).isEqualTo("catalog:write");
    }

    @Test
    void issueToken_withNoRequestedScope_issuesEveryGrantedScope() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(consumer));
        when(registeredServiceRepository.findByName("product-catalog-service")).thenReturn(Optional.of(producer));
        ConsumerGrant grant = new ConsumerGrant(consumer.getId(), producer.getId(), "catalog:write catalog:read");
        when(consumerGrantRepository.findByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId()))
                .thenReturn(Optional.of(grant));

        TokenResponse response = tokenService.issueToken(request(RAW_SECRET, "product-catalog-service", null));

        assertThat(response.scope().split(" ")).containsExactlyInAnyOrder("catalog:write", "catalog:read");
    }

    @Test
    void issueToken_withWrongSecret_throwsInvalidClientCredentials() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(consumer));

        assertThatThrownBy(() -> tokenService.issueToken(request("wrong-secret", "product-catalog-service", null)))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    void issueToken_withUnknownClientId_throwsInvalidClientCredentials() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.issueToken(request(RAW_SECRET, "product-catalog-service", null)))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    void issueToken_withNonConsumerCaller_throwsNotAConsumer() {
        RegisteredService producerOnlyCaller = new RegisteredService(
                "product-catalog-service", "Product Catalog", null, ServiceRole.PRODUCER,
                "client-order", passwordEncoder.encode(RAW_SECRET));
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(producerOnlyCaller));

        assertThatThrownBy(() -> tokenService.issueToken(request(RAW_SECRET, "some-other-service", null)))
                .isInstanceOf(NotAConsumerException.class);
    }

    @Test
    void issueToken_withUnknownAudience_throwsUnknownAudience() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(consumer));
        when(registeredServiceRepository.findByName("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.issueToken(request(RAW_SECRET, "does-not-exist", null)))
                .isInstanceOf(UnknownAudienceException.class);
    }

    @Test
    void issueToken_withMissingGrant_throwsGrantNotFound() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(consumer));
        when(registeredServiceRepository.findByName("product-catalog-service")).thenReturn(Optional.of(producer));
        when(consumerGrantRepository.findByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.issueToken(request(RAW_SECRET, "product-catalog-service", null)))
                .isInstanceOf(GrantNotFoundException.class);
    }

    @Test
    void issueToken_requestingUngrantedScope_throwsNoRequestedScopesGranted() {
        when(registeredServiceRepository.findByClientId("client-order")).thenReturn(Optional.of(consumer));
        when(registeredServiceRepository.findByName("product-catalog-service")).thenReturn(Optional.of(producer));
        ConsumerGrant grant = new ConsumerGrant(consumer.getId(), producer.getId(), "catalog:read");
        when(consumerGrantRepository.findByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId()))
                .thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> tokenService.issueToken(
                request(RAW_SECRET, "product-catalog-service", List.of("catalog:write"))))
                .isInstanceOf(NoRequestedScopesGrantedException.class);
    }
}
