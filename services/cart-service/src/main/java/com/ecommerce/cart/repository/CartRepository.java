package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByOwnerIdAndStatus(UUID ownerId, CartStatus status);

    /** The caller's most recent cart in any status. */
    Optional<Cart> findFirstByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /** Row-locks the cart so add/update/remove/clear/checkout serialize per cart. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id = :id")
    Optional<Cart> findByIdForUpdate(@Param("id") UUID id);

    long countByOwnerIdAndStatus(UUID ownerId, CartStatus status);
}
