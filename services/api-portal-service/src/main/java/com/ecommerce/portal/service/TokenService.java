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
import com.ecommerce.portal.repository.ConsumerGrantRepository;
import com.ecommerce.portal.repository.RegisteredServiceRepository;
import com.ecommerce.portal.security.PortalJwtService;
import com.ecommerce.portal.util.Scopes;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Layered architecture, service tier: implements the OAuth2 client-credentials
 * grant (RFC 6749 §4.4) for service-to-service calls. Talks to persistence
 * only through the repositories (Repository pattern), to secret
 * verification only through {@link PasswordEncoder} (Strategy pattern), and
 * to token signing only through {@link PortalJwtService}.
 *
 * <p>Authorization is checked in this order, matching the shape of the
 * eventual JWT: caller identity (clientId/clientSecret) -> caller role
 * (must be CONSUMER or BOTH) -> target audience (must be an active,
 * registered PRODUCER or BOTH) -> grant (must exist for this pair) -> scope
 * (issued = requested ∩ granted, and must be non-empty).
 */
@Service
public class TokenService {

    private final RegisteredServiceRepository registeredServiceRepository;
    private final ConsumerGrantRepository consumerGrantRepository;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    public TokenService(RegisteredServiceRepository registeredServiceRepository,
                         ConsumerGrantRepository consumerGrantRepository,
                         PasswordEncoder passwordEncoder,
                         PortalJwtService portalJwtService) {
        this.registeredServiceRepository = registeredServiceRepository;
        this.consumerGrantRepository = consumerGrantRepository;
        this.passwordEncoder = passwordEncoder;
        this.portalJwtService = portalJwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse issueToken(TokenRequest request) {
        RegisteredService consumer = registeredServiceRepository.findByClientId(request.clientId())
                .filter(RegisteredService::isActive)
                // Unknown clientId and a wrong clientSecret get the identical generic
                // failure below - no client-enumeration, mirroring auth-service's login.
                .orElseThrow(InvalidClientCredentialsException::new);

        if (!passwordEncoder.matches(request.clientSecret(), consumer.getClientSecretHash())) {
            throw new InvalidClientCredentialsException();
        }

        if (!consumer.getRole().isConsumer()) {
            throw new NotAConsumerException(consumer.getName());
        }

        String audience = request.audience().trim().toLowerCase();
        RegisteredService producer = registeredServiceRepository.findByName(audience)
                .filter(RegisteredService::isActive)
                .filter(service -> service.getRole().isProducer())
                .orElseThrow(() -> new UnknownAudienceException(audience));

        ConsumerGrant grant = consumerGrantRepository
                .findByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId())
                .orElseThrow(() -> new GrantNotFoundException(consumer.getName(), producer.getName()));

        Set<String> grantedScopes = Scopes.asSet(grant.getScopes());
        // Trim each requested scope before comparing - incidental whitespace (e.g. a
        // client accidentally sending "catalog:write " or " catalog:write") must not
        // fail to match an otherwise-identical granted scope.
        Set<String> requestedScopes = (request.scope() == null || request.scope().isEmpty())
                ? grantedScopes
                : request.scope().stream()
                        .map(String::trim)
                        .filter(scope -> !scope.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> issuedScopes = new LinkedHashSet<>(grantedScopes);
        issuedScopes.retainAll(requestedScopes);

        if (issuedScopes.isEmpty()) {
            throw new NoRequestedScopesGrantedException(consumer.getName(), producer.getName());
        }

        String accessToken = portalJwtService.generateToken(consumer.getName(), producer.getName(), issuedScopes);
        return new TokenResponse(accessToken, portalJwtService.expirationSeconds(), String.join(" ", issuedScopes));
    }
}
