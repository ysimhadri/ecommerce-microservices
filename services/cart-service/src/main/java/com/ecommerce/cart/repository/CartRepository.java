package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository pattern: persistence for {@link Cart}, isolated from {@code CartService}. */
public interface CartRepository extends JpaRepository<Cart, UUID> {
}
