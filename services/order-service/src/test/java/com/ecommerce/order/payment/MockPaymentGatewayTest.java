package com.ecommerce.order.payment;

import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.model.PaymentAuthorization;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.repository.PaymentAuthorizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayTest {

    @Mock
    private PaymentAuthorizationRepository paymentAuthorizationRepository;

    @InjectMocks
    private MockPaymentGateway paymentGateway;

    @Test
    void authorize_whenSimulated_recordsNothing() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> paymentGateway.authorize(orderId, new BigDecimal("10.00"), true))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(paymentAuthorizationRepository, never()).save(any());
    }

    @Test
    void void_recordsAVoid_andIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        when(paymentAuthorizationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentAuthorizationRepository.save(any(PaymentAuthorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentGateway.voidAuthorization(orderId);

        ArgumentCaptor<PaymentAuthorization> captor = ArgumentCaptor.forClass(PaymentAuthorization.class);
        verify(paymentAuthorizationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.VOIDED);

        PaymentAuthorization existing = captor.getValue();
        when(paymentAuthorizationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));
        paymentGateway.voidAuthorization(orderId);
        verify(paymentAuthorizationRepository).save(any());
        assertThat(existing.getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void void_existingAuthorization_marksItVoidedAndDoesNotInsertAnotherRow() {
        UUID orderId = UUID.randomUUID();
        PaymentAuthorization existing = new PaymentAuthorization(orderId, new BigDecimal("10.00"), PaymentStatus.AUTHORIZED);
        when(paymentAuthorizationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        paymentGateway.voidAuthorization(orderId);
        paymentGateway.voidAuthorization(orderId);

        assertThat(existing.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        verify(paymentAuthorizationRepository, never()).save(any());
    }
}
