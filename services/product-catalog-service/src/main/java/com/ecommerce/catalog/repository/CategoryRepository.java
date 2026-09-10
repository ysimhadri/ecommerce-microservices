package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository pattern: isolates persistence access to {@link Category}
 * behind a narrow interface so the command/query services never talk to
 * JPA/SQL directly.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
