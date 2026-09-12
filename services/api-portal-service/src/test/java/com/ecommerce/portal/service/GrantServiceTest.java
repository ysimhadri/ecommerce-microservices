package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.GrantCreateRequest;
import com.ecommerce.portal.dto.GrantResponse;
import com.ecommerce.portal.exception.DuplicateGrantException;
import com.ecommerce.portal.exception.NotAConsumerException;
import com.ecommerce.portal.exception.NotAProducerException;
import com.ecommerce.portal.model.ConsumerGrant;
import com.ecommerce.portal.model.RegisteredService;
import com.ecommerce.portal.model.ServiceRole;
import com.ecommerce.portal.repository.ConsumerGrantRepository;
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
 * Unit tests for the grant service-layer logic: role checks and the
 * one-grant-per-pair rule, with persistence mocked out. v1 auto-approves
 * every grant - there is no separate "approve" step to test.
 */
@ExtendWith(MockitoExtension.class)
class GrantServiceTest {

    @Mock
    private ServiceRegistryService serviceRegistryService;

    @Mock
    private ConsumerGrantRepository consumerGrantRepository;

    @InjectMocks
    private GrantService grantService;

    private RegisteredService consumer(ServiceRole role) {
        return new RegisteredService("order-service", "Order Service", null, role, "client-consumer", "hash");
    }

    private RegisteredService producer(ServiceRole role) {
        return new RegisteredService("product-catalog-service", "Product Catalog", null, role, "client-producer", "hash");
    }

    @Test
    void createGrant_forValidConsumerAndProducer_autoApprovesAndPersists() {
        RegisteredService consumer = consumer(ServiceRole.CONSUMER);
        RegisteredService producer = producer(ServiceRole.PRODUCER);
        when(serviceRegistryService.getEntity(consumer.getId())).thenReturn(consumer);
        when(serviceRegistryService.getEntity(producer.getId())).thenReturn(producer);
        when(consumerGrantRepository.existsByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId()))
                .thenReturn(false);
        when(consumerGrantRepository.saveAndFlush(any(ConsumerGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GrantResponse response = grantService.createGrant(
                new GrantCreateRequest(consumer.getId(), producer.getId(), List.of("catalog:write")));

        assertThat(response.consumerServiceName()).isEqualTo("order-service");
        assertThat(response.producerServiceName()).isEqualTo("product-catalog-service");
        assertThat(response.scopes()).containsExactly("catalog:write");
    }

    @Test
    void createGrant_withConsumerServiceLackingConsumerRole_throwsNotAConsumer() {
        RegisteredService producerOnly = consumer(ServiceRole.PRODUCER); // wrong role for the "consumer" side
        when(serviceRegistryService.getEntity(producerOnly.getId())).thenReturn(producerOnly);

        assertThatThrownBy(() -> grantService.createGrant(
                new GrantCreateRequest(producerOnly.getId(), java.util.UUID.randomUUID(), List.of("catalog:write"))))
                .isInstanceOf(NotAConsumerException.class);

        verify(consumerGrantRepository, never()).saveAndFlush(any(ConsumerGrant.class));
    }

    @Test
    void createGrant_withProducerServiceLackingProducerRole_throwsNotAProducer() {
        RegisteredService consumer = consumer(ServiceRole.CONSUMER);
        RegisteredService consumerOnlyAsProducer = producer(ServiceRole.CONSUMER); // wrong role for the "producer" side
        when(serviceRegistryService.getEntity(consumer.getId())).thenReturn(consumer);
        when(serviceRegistryService.getEntity(consumerOnlyAsProducer.getId())).thenReturn(consumerOnlyAsProducer);

        assertThatThrownBy(() -> grantService.createGrant(
                new GrantCreateRequest(consumer.getId(), consumerOnlyAsProducer.getId(), List.of("catalog:write"))))
                .isInstanceOf(NotAProducerException.class);

        verify(consumerGrantRepository, never()).saveAndFlush(any(ConsumerGrant.class));
    }

    @Test
    void createGrant_whenGrantAlreadyExistsForPair_throwsDuplicateGrant() {
        RegisteredService consumer = consumer(ServiceRole.CONSUMER);
        RegisteredService producer = producer(ServiceRole.PRODUCER);
        when(serviceRegistryService.getEntity(consumer.getId())).thenReturn(consumer);
        when(serviceRegistryService.getEntity(producer.getId())).thenReturn(producer);
        when(consumerGrantRepository.existsByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> grantService.createGrant(
                new GrantCreateRequest(consumer.getId(), producer.getId(), List.of("catalog:write"))))
                .isInstanceOf(DuplicateGrantException.class);

        verify(consumerGrantRepository, never()).saveAndFlush(any(ConsumerGrant.class));
    }
}
