package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.AuthorizeOutcome;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.exception.PaymentExceptions.InvalidPaymentRequestException;
import com.ecommerce.payment.exception.PaymentExceptions.PaymentUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulated acquirer. The idempotency key is {@code orderId}. A stored
 * {@code AUTHORIZED} or {@code VOIDED} row wins over a later {@code simulateDecline}.
 */
@Service
public class PaymentService {

    private final PaymentTransactions transactions;

    public PaymentService(PaymentTransactions transactions) {
        this.transactions = transactions;
    }

    public AuthorizeOutcome authorize(UUID userId,
                                      UUID orderId,
                                      BigDecimal amount,
                                      String currency,
                                      boolean simulateDecline,
                                      boolean simulateOutage) {
        if (simulateOutage) {
            throw new PaymentUnavailableException("Payment service is unavailable");
        }
        BigDecimal scaled = normalizeAmount(amount);
        String normalized = normalizeCurrency(currency);
        try {
            return transactions.authorize(userId, orderId, scaled, normalized, simulateDecline);
        } catch (DataIntegrityViolationException concurrentInsert) {
            return transactions.replay(userId, orderId, scaled, normalized);
        }
    }

    public Optional<PaymentResponse> voidAuthorization(UUID userId, UUID orderId) {
        return transactions.voidAuthorization(userId, orderId);
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentRequestException("amount: must be greater than zero");
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentRequestException("amount: must be greater than zero");
        }
        return scaled;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new InvalidPaymentRequestException("currency: must not be blank");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new InvalidPaymentRequestException("currency: must be a 3-letter code");
        }
        return normalized;
    }
}
