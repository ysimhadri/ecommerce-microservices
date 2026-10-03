package com.ecommerce.eligibility.model;

/**
 * Financial products this service can score eligibility for.
 * {@code CREDIT_CARD} is the worked example on the public API.
 */
public enum ProductType {
    CREDIT_CARD,
    PERSONAL_LOAN,
    MORTGAGE
}
