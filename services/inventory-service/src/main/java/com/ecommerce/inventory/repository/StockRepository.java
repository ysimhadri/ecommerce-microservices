package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
}
