package com.ecommerce.order.payment;

import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.model.PaymentAuthorization;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.repository.PaymentAuthorizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Approves every authorization unless the caller sets {@code simulatePaymentFailure}.
 * A decline throws before any row is written, so the saga treats the step as
 * not completed and does not void it. Void records a void locally; nothing is captured.
 */
@Service
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentAuthorizationRepository paymentAuthorizationRepository;

    public MockPaymentGateway(PaymentAuthorizationRepository paymentAuthorizationRepository) {
        this.paymentAuthorizationRepository = paymentAuthorizationRepository;
    }

    @Override
    @Transactional
    public void authorize(UUID orderId, BigDecimal amount, boolean simulatePaymentFailure) {
        if (simulatePaymentFailure) {
            throw new PaymentDeclinedException("Payment was declined");
        }
        if (paymentAuthorizationRepository.findByOrderId(orderId).isPresent()) {
            return;
        }
        paymentAuthorizationRepository.save(new PaymentAuthorization(orderId, amount, PaymentStatus.AUTHORIZED));
    }

    @Override
    @Transactional
    public void voidAuthorization(UUID orderId) {
        PaymentAuthorization existing = paymentAuthorizationRepository.findByOrderId(orderId).orElse(null);
        if (existing == null) {
            paymentAuthorizationRepository.save(new PaymentAuthorization(orderId, BigDecimal.ZERO.setScale(2), PaymentStatus.VOIDED));
            return;
        }
        if (existing.getStatus() == PaymentStatus.VOIDED) {
            return;
        }
        existing.markVoided();
    }
}
