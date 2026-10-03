package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddItemRequest(
        @NotNull UUID productId,
        @Min(value = 1, message = "must be greater than 0") int quantity) {
}
