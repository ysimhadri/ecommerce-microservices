package com.ecommerce.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Authorize one order. {@code simulateDecline} and {@code simulateOutage} default
 * to false. The order saga sends {@code simulateDecline} from its existing
 * {@code simulatePaymentFailure} flag and does not send {@code simulateOutage}.
 */
public record AuthorizePaymentRequest(
        @NotNull(message = "must not be null") UUID orderId,
        @NotNull(message = "must not be null") @Positive(message = "must be greater than zero") BigDecimal amount,
        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter code") String currency,
        Boolean simulateDecline,
        Boolean simulateOutage
) {
}
