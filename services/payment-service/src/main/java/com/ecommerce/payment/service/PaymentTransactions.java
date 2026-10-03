package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.AuthorizeOutcome;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.exception.PaymentExceptions.PaymentConflictException;
import com.ecommerce.payment.exception.PaymentExceptions.PaymentDeclinedException;
import com.ecommerce.payment.exception.PaymentExceptions.PaymentForbiddenException;
import com.ecommerce.payment.model.PaymentAuthorization;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentAuthorizationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction boundary for the ledger. {@link PaymentService} calls these
 * methods from outside the class so a unique-constraint race can start a new
 * transaction after the failed insert rolls back.
 */
@Component
public class PaymentTransactions {

    private final PaymentAuthorizationRepository paymentAuthorizationRepository;

    public PaymentTransactions(PaymentAuthorizationRepository paymentAuthorizationRepository) {
        this.paymentAuthorizationRepository = paymentAuthorizationRepository;
    }

    @Transactional
    public AuthorizeOutcome authorize(UUID userId, UUID orderId, BigDecimal amount, String currency, boolean simulateDecline) {
        PaymentAuthorization existing = paymentAuthorizationRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return sameCharge(existing, userId, amount, currency);
        }
        if (simulateDecline) {
            throw new PaymentDeclinedException("Payment was declined");
        }
        PaymentAuthorization created = paymentAuthorizationRepository.saveAndFlush(
                new PaymentAuthorization(orderId, userId, amount, currency));
        return new AuthorizeOutcome(toResponse(created), true);
    }

    @Transactional(readOnly = true)
    public AuthorizeOutcome replay(UUID userId, UUID orderId, BigDecimal amount, String currency) {
        PaymentAuthorization existing = paymentAuthorizationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Payment row missing after unique conflict"));
        return sameCharge(existing, userId, amount, currency);
    }

    /**
     * {@code AUTHORIZED} becomes {@code VOIDED}. A second void stays {@code VOIDED}.
     * A missing row is empty and inserts nothing.
     */
    @Transactional
    public Optional<PaymentResponse> voidAuthorization(UUID userId, UUID orderId) {
        PaymentAuthorization existing = paymentAuthorizationRepository.findByOrderId(orderId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.getUserId().equals(userId)) {
            throw new PaymentForbiddenException("Payment belongs to another user");
        }
        if (existing.getStatus() != PaymentStatus.VOIDED) {
            existing.markVoided();
        }
        return Optional.of(toResponse(existing));
    }

    private static AuthorizeOutcome sameCharge(PaymentAuthorization existing, UUID userId, BigDecimal amount, String currency) {
        if (!existing.getUserId().equals(userId)) {
            throw new PaymentForbiddenException("Payment belongs to another user");
        }
        if (existing.getAmount().compareTo(amount) != 0 || !existing.getCurrency().equals(currency)) {
            throw new PaymentConflictException(
                    "Payment already exists for this order with a different amount or currency");
        }
        return new AuthorizeOutcome(toResponse(existing), false);
    }

    private static PaymentResponse toResponse(PaymentAuthorization payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus());
    }
}
