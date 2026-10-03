package com.ecommerce.eligibility.repository;

import com.ecommerce.eligibility.model.CustomerEligibilityHistory;
import com.ecommerce.eligibility.model.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerEligibilityHistoryRepository extends JpaRepository<CustomerEligibilityHistory, UUID> {

    Optional<CustomerEligibilityHistory> findByCustomerIdAndProductType(String customerId, ProductType productType);
}
