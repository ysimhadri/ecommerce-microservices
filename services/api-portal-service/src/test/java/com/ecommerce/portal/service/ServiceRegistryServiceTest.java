package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.ApiDeclareRequest;
import com.ecommerce.portal.dto.ApiResponse;
import com.ecommerce.portal.dto.ServiceCreatedResponse;
import com.ecommerce.portal.dto.ServiceRegisterRequest;
import com.ecommerce.portal.exception.DuplicateServiceNameException;
import com.ecommerce.portal.exception.NotAProducerException;
import com.ecommerce.portal.exception.ServiceNotFoundException;
import com.ecommerce.portal.model.ProducerApi;
import com.ecommerce.portal.model.RegisteredService;
import com.ecommerce.portal.model.ServiceRole;
import com.ecommerce.portal.repository.ProducerApiRepository;
import com.ecommerce.portal.repository.RegisteredServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the registry service-layer logic: register, rotate-secret,
 * and producer-API declaration, with persistence and hashing mocked out.
 */
@ExtendWith(MockitoExtension.class)
class ServiceRegistryServiceTest {

    @Mock
    private RegisteredServiceRepository registeredServiceRepository;

    @Mock
    private ProducerApiRepository producerApiRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ServiceRegistryService serviceRegistryService;

    private static final String NAME = "product-catalog-service";

    @Test
    void register_withNewName_persistsHashedSecretAndReturnsPlaintextOnce() {
        when(registeredServiceRepository.existsByName(NAME)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(registeredServiceRepository.saveAndFlush(any(RegisteredService.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceCreatedResponse response = serviceRegistryService.register(
                new ServiceRegisterRequest(NAME, "Product Catalog", null, ServiceRole.PRODUCER));

        assertThat(response.name()).isEqualTo(NAME);
        assertThat(response.clientSecret()).isNotBlank();
        assertThat(response.clientId()).startsWith(NAME + "-");
        // The response never carries the hash - only the freshly-generated plaintext.
        assertThat(response.clientSecret()).isNotEqualTo("bcrypt-hash");
        verify(registeredServiceRepository).saveAndFlush(any(RegisteredService.class));
    }

    @Test
    void register_withDuplicateName_throwsAndNeverPersists() {
        when(registeredServiceRepository.existsByName(NAME)).thenReturn(true);

        assertThatThrownBy(() -> serviceRegistryService.register(
                new ServiceRegisterRequest(NAME, "Product Catalog", null, ServiceRole.PRODUCER)))
                .isInstanceOf(DuplicateServiceNameException.class);

        verify(registeredServiceRepository, never()).saveAndFlush(any(RegisteredService.class));
    }

    @Test
    void rotateSecret_returnsNewPlaintextSecretAndUpdatesStoredHash() {
        RegisteredService service = new RegisteredService(
                NAME, "Product Catalog", null, ServiceRole.PRODUCER, "client-1", "old-hash");
        when(registeredServiceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        var response = serviceRegistryService.rotateSecret(service.getId());

        assertThat(response.clientId()).isEqualTo("client-1");
        assertThat(response.clientSecret()).isNotBlank();
        assertThat(service.getClientSecretHash()).isEqualTo("new-hash");
    }

    @Test
    void get_withUnknownId_throwsServiceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(registeredServiceRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceRegistryService.get(unknownId))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    void declareApi_forProducerRole_succeeds() {
        RegisteredService producer = new RegisteredService(
                NAME, "Product Catalog", null, ServiceRole.PRODUCER, "client-1", "hash");
        when(registeredServiceRepository.findById(producer.getId())).thenReturn(Optional.of(producer));
        when(producerApiRepository.save(any(ProducerApi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse response = serviceRegistryService.declareApi(
                producer.getId(), new ApiDeclareRequest("POST", "/api/v1/catalog/products", List.of("catalog:write")));

        assertThat(response.method()).isEqualTo("POST");
        assertThat(response.requiredScopes()).containsExactly("catalog:write");
    }

    @Test
    void declareApi_forConsumerOnlyRole_throwsNotAProducer() {
        RegisteredService consumerOnly = new RegisteredService(
                "order-service", "Order Service", null, ServiceRole.CONSUMER, "client-2", "hash");
        when(registeredServiceRepository.findById(consumerOnly.getId())).thenReturn(Optional.of(consumerOnly));

        assertThatThrownBy(() -> serviceRegistryService.declareApi(
                consumerOnly.getId(), new ApiDeclareRequest("POST", "/api/v1/orders", List.of("orders:write"))))
                .isInstanceOf(NotAProducerException.class);

        verify(producerApiRepository, never()).save(any(ProducerApi.class));
    }
}
