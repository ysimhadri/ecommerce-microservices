package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartIdOrderByProductNameAscIdAsc(UUID cartId);

    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId);

    void deleteByCartId(UUID cartId);

    long countByCartId(UUID cartId);
}
