package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cancel keeps the order and its lines. Confirm moves PENDING to CONFIRMED. */
@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderCommandService orderCommandService;

    private final AtomicReference<CustomerOrder> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(orderRepository.findById(any())).thenAnswer(invocation -> {
            CustomerOrder order = stored.get();
            if (order != null && order.getId().equals(invocation.getArgument(0))) {
                return Optional.of(order);
            }
            return Optional.empty();
        });
    }

    @Test
    void createPending_snapshotsPrice_confirmAndCancelDoNotDeleteLines() {
        UUID ownerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        OrderResponse pending = orderCommandService.createPending(ownerId, cartId, List.of(
                new PricedLine(productId, "Headphones", new BigDecimal("9.99"), 2)));

        assertThat(pending.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(pending.failureCode()).isNull();
        assertThat(pending.total()).isEqualByComparingTo("19.98");
        assertThat(pending.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo("Headphones");
            assertThat(line.lineTotal()).isEqualByComparingTo("19.98");
        });

        OrderResponse confirmed = orderCommandService.confirm(pending.orderId());
        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.lines()).hasSize(1);

        OrderResponse cancelled = orderCommandService.cancel(pending.orderId(), "PAYMENT_DECLINED");
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.failureCode()).isEqualTo("PAYMENT_DECLINED");
        assertThat(cancelled.lines()).hasSize(1);
        assertThat(stored.get().getLines()).hasSize(1);

        verify(orderRepository, never()).delete(any());
        verify(orderRepository, never()).deleteById(any());
    }
}
