package com.ecommerce.order.repository;

import com.ecommerce.order.model.PaymentAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAuthorizationRepository extends JpaRepository<PaymentAuthorization, UUID> {

    Optional<PaymentAuthorization> findByOrderId(UUID orderId);
}
