package com.ecommerce.portal.repository;

import com.ecommerce.portal.model.RegisteredService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository pattern: isolates persistence access to {@link RegisteredService}
 * behind a narrow interface so the service tier never talks to JPA/SQL
 * directly.
 */
public interface RegisteredServiceRepository extends JpaRepository<RegisteredService, UUID> {

    Optional<RegisteredService> findByName(String name);

    boolean existsByName(String name);

    Optional<RegisteredService> findByClientId(String clientId);
}
