package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.GrantCreateRequest;
import com.ecommerce.portal.dto.GrantResponse;
import com.ecommerce.portal.exception.DuplicateGrantException;
import com.ecommerce.portal.exception.NotAConsumerException;
import com.ecommerce.portal.exception.NotAProducerException;
import com.ecommerce.portal.model.ConsumerGrant;
import com.ecommerce.portal.model.RegisteredService;
import com.ecommerce.portal.repository.ConsumerGrantRepository;
import com.ecommerce.portal.util.Scopes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Layered architecture, service tier: owns granting a consumer service
 * scoped access to a producer service. v1 auto-approves every grant at
 * creation time - there is no human approval workflow - so the only checks
 * are that both services exist and hold the right role, and that this
 * exact (consumer, producer) pair hasn't already been granted (see the
 * migration's UNIQUE constraint).
 */
@Service
public class GrantService {

    private final ServiceRegistryService serviceRegistryService;
    private final ConsumerGrantRepository consumerGrantRepository;

    public GrantService(ServiceRegistryService serviceRegistryService, ConsumerGrantRepository consumerGrantRepository) {
        this.serviceRegistryService = serviceRegistryService;
        this.consumerGrantRepository = consumerGrantRepository;
    }

    @Transactional
    public GrantResponse createGrant(GrantCreateRequest request) {
        RegisteredService consumer = serviceRegistryService.getEntity(request.consumerServiceId());
        if (!consumer.getRole().isConsumer()) {
            throw new NotAConsumerException(consumer.getName());
        }

        RegisteredService producer = serviceRegistryService.getEntity(request.producerServiceId());
        if (!producer.getRole().isProducer()) {
            throw new NotAProducerException(producer.getName());
        }

        if (consumerGrantRepository.existsByConsumerServiceIdAndProducerServiceId(consumer.getId(), producer.getId())) {
            throw new DuplicateGrantException(consumer.getName(), producer.getName());
        }

        ConsumerGrant grant = new ConsumerGrant(consumer.getId(), producer.getId(), Scopes.join(request.scopes()));
        try {
            // saveAndFlush, not save: ConsumerGrant.id is a client-assigned UUID - without
            // this, a concurrent duplicate-grant race's constraint violation would defer
            // past this method's return and never reach the catch below (see
            // RegisteredService/Category/User's javadoc for the full explanation).
            ConsumerGrant saved = consumerGrantRepository.saveAndFlush(grant);
            return toResponse(saved, consumer.getName(), producer.getName());
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // A concurrent request won the race between our existsBy... check and this
            // save() - the unique (consumer, producer) constraint caught it; report it
            // the same way as the pre-check does.
            throw new DuplicateGrantException(consumer.getName(), producer.getName());
        }
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> list(UUID consumerServiceId, UUID producerServiceId) {
        List<ConsumerGrant> grants;
        if (consumerServiceId != null && producerServiceId != null) {
            grants = consumerGrantRepository.findByConsumerServiceIdAndProducerServiceId(consumerServiceId, producerServiceId)
                    .map(List::of)
                    .orElseGet(List::of);
        } else if (consumerServiceId != null) {
            grants = consumerGrantRepository.findByConsumerServiceId(consumerServiceId);
        } else if (producerServiceId != null) {
            grants = consumerGrantRepository.findByProducerServiceId(producerServiceId);
        } else {
            grants = consumerGrantRepository.findAll();
        }

        return grants.stream()
                .map(grant -> toResponse(
                        grant,
                        serviceRegistryService.getEntity(grant.getConsumerServiceId()).getName(),
                        serviceRegistryService.getEntity(grant.getProducerServiceId()).getName()))
                .toList();
    }

    private GrantResponse toResponse(ConsumerGrant grant, String consumerName, String producerName) {
        return new GrantResponse(
                grant.getId(), grant.getConsumerServiceId(), consumerName,
                grant.getProducerServiceId(), producerName,
                Scopes.asList(grant.getScopes()), grant.getCreatedAt());
    }
}
