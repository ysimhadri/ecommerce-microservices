package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;

public record UpdateItemRequest(@Min(value = 1, message = "must be greater than 0") int quantity) {
}
