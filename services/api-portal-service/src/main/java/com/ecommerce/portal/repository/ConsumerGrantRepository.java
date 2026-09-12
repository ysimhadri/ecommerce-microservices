package com.ecommerce.portal.repository;

import com.ecommerce.portal.model.ConsumerGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pattern: isolates persistence access to {@link ConsumerGrant}
 * behind a narrow interface so the service tier never talks to JPA/SQL
 * directly.
 */
public interface ConsumerGrantRepository extends JpaRepository<ConsumerGrant, UUID> {

    Optional<ConsumerGrant> findByConsumerServiceIdAndProducerServiceId(UUID consumerServiceId, UUID producerServiceId);

    boolean existsByConsumerServiceIdAndProducerServiceId(UUID consumerServiceId, UUID producerServiceId);

    List<ConsumerGrant> findByConsumerServiceId(UUID consumerServiceId);

    List<ConsumerGrant> findByProducerServiceId(UUID producerServiceId);
}
