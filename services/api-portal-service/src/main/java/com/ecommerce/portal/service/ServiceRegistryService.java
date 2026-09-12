package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.ApiDeclareRequest;
import com.ecommerce.portal.dto.ApiResponse;
import com.ecommerce.portal.dto.RotateSecretResponse;
import com.ecommerce.portal.dto.ServiceCreatedResponse;
import com.ecommerce.portal.dto.ServiceRegisterRequest;
import com.ecommerce.portal.dto.ServiceResponse;
import com.ecommerce.portal.exception.DuplicateServiceNameException;
import com.ecommerce.portal.exception.NotAProducerException;
import com.ecommerce.portal.exception.ServiceNotFoundException;
import com.ecommerce.portal.model.ProducerApi;
import com.ecommerce.portal.model.RegisteredService;
import com.ecommerce.portal.repository.ProducerApiRepository;
import com.ecommerce.portal.repository.RegisteredServiceRepository;
import com.ecommerce.portal.util.Scopes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Layered architecture, service tier: owns service registration, secret
 * rotation, and producer-API declaration. Talks to persistence only
 * through {@link RegisteredServiceRepository} / {@link ProducerApiRepository}
 * (Repository pattern) and to hashing only through {@link PasswordEncoder}
 * (Strategy pattern) - never JPA or a concrete hashing algorithm directly.
 */
@Service
public class ServiceRegistryService {

    /** 96 bits of randomness per generated token half - plenty to make guessing infeasible, short enough to stay curl-friendly. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegisteredServiceRepository registeredServiceRepository;
    private final ProducerApiRepository producerApiRepository;
    private final PasswordEncoder passwordEncoder;

    public ServiceRegistryService(RegisteredServiceRepository registeredServiceRepository,
                                   ProducerApiRepository producerApiRepository,
                                   PasswordEncoder passwordEncoder) {
        this.registeredServiceRepository = registeredServiceRepository;
        this.producerApiRepository = producerApiRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ServiceCreatedResponse register(ServiceRegisterRequest request) {
        // No .toLowerCase() here: ServiceRegisterRequest.name's @Pattern already rejects
        // any uppercase input at the bean-validation layer before this method ever runs.
        String name = request.name().trim();

        if (registeredServiceRepository.existsByName(name)) {
            throw new DuplicateServiceNameException(name);
        }

        String clientId = name + "-" + randomToken(12);
        String clientSecret = randomToken(32);

        RegisteredService service = new RegisteredService(
                name, request.displayName().trim(), request.baseUrl(), request.role(),
                clientId, passwordEncoder.encode(clientSecret));

        try {
            // saveAndFlush, not save: see RegisteredService's javadoc - a client-assigned
            // UUID id needs the explicit flush to make the INSERT (and any constraint
            // violation) happen synchronously, inside this catch.
            RegisteredService saved = registeredServiceRepository.saveAndFlush(service);
            return new ServiceCreatedResponse(
                    saved.getId(), saved.getName(), saved.getDisplayName(), saved.getBaseUrl(), saved.getRole(),
                    saved.getClientId(), clientSecret, saved.isActive(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // A concurrent request won the race between our existsByName check and this
            // save() - the unique constraint on name caught it; report it the same way.
            throw new DuplicateServiceNameException(name);
        }
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list() {
        return registeredServiceRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public RotateSecretResponse rotateSecret(UUID id) {
        RegisteredService service = getEntity(id);
        String clientSecret = randomToken(32);
        service.setClientSecretHash(passwordEncoder.encode(clientSecret));
        return new RotateSecretResponse(service.getId(), service.getClientId(), clientSecret);
    }

    @Transactional
    public ApiResponse declareApi(UUID producerServiceId, ApiDeclareRequest request) {
        RegisteredService producer = getEntity(producerServiceId);
        if (!producer.getRole().isProducer()) {
            throw new NotAProducerException(producer.getName());
        }

        ProducerApi api = new ProducerApi(
                producer.getId(), request.method().toUpperCase(), request.pathPattern().trim(),
                Scopes.join(request.requiredScopes()));
        return toApiResponse(producerApiRepository.save(api));
    }

    @Transactional(readOnly = true)
    public List<ApiResponse> listApis(UUID producerServiceId) {
        getEntity(producerServiceId); // 404 if the service itself doesn't exist
        return producerApiRepository.findByProducerServiceId(producerServiceId).stream()
                .map(this::toApiResponse)
                .toList();
    }

    /** Exposed so {@code GrantService} can resolve a service by id without duplicating this lookup+404 logic. */
    public RegisteredService getEntity(UUID id) {
        return registeredServiceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException(id));
    }

    private String randomToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ServiceResponse toResponse(RegisteredService service) {
        return new ServiceResponse(
                service.getId(), service.getName(), service.getDisplayName(), service.getBaseUrl(),
                service.getRole(), service.getClientId(), service.isActive(), service.getCreatedAt());
    }

    private ApiResponse toApiResponse(ProducerApi api) {
        return new ApiResponse(
                api.getId(), api.getProducerServiceId(), api.getMethod(), api.getPathPattern(),
                Scopes.asList(api.getRequiredScopes()), api.getCreatedAt());
    }
}
