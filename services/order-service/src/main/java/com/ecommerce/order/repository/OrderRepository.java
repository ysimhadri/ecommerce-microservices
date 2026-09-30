package com.ecommerce.order.repository;

import com.ecommerce.order.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {
}
