package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Repository pattern: isolates persistence access to {@link Product} behind
 * a narrow interface. The search methods use native {@code ILIKE} queries
 * (rather than JPQL {@code LOWER(...) LIKE ...}) so Postgres can actually
 * use the {@code pg_trgm} GIN indexes from
 * {@code V2__create_products_table.sql} instead of a sequential scan.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

    @Query(
            value = "SELECT * FROM products WHERE name ILIKE CONCAT('%', :term, '%') "
                    + "OR description ILIKE CONCAT('%', :term, '%')",
            countQuery = "SELECT count(*) FROM products WHERE name ILIKE CONCAT('%', :term, '%') "
                    + "OR description ILIKE CONCAT('%', :term, '%')",
            nativeQuery = true)
    Page<Product> searchByTerm(@Param("term") String term, Pageable pageable);

    @Query(
            value = "SELECT * FROM products WHERE category_id = :categoryId AND "
                    + "(name ILIKE CONCAT('%', :term, '%') OR description ILIKE CONCAT('%', :term, '%'))",
            countQuery = "SELECT count(*) FROM products WHERE category_id = :categoryId AND "
                    + "(name ILIKE CONCAT('%', :term, '%') OR description ILIKE CONCAT('%', :term, '%'))",
            nativeQuery = true)
    Page<Product> searchByCategoryIdAndTerm(@Param("categoryId") UUID categoryId,
                                             @Param("term") String term,
                                             Pageable pageable);
}
