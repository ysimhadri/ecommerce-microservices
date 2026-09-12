package com.ecommerce.portal.repository;

import com.ecommerce.portal.model.ProducerApi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository pattern: isolates persistence access to {@link ProducerApi}
 * behind a narrow interface so the service tier never talks to JPA/SQL
 * directly.
 */
public interface ProducerApiRepository extends JpaRepository<ProducerApi, UUID> {

    List<ProducerApi> findByProducerServiceId(UUID producerServiceId);
}
