package com.ecommerce.eligibility.repository;

import com.ecommerce.eligibility.model.EligibilityDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EligibilityDecisionRepository extends JpaRepository<EligibilityDecision, UUID> {
}
