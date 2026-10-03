package com.ecommerce.order.saga;

import com.ecommerce.order.client.CartClient;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.ClientSnapshots.CartLineSnapshot;
import com.ecommerce.order.client.ClientSnapshots.CartSnapshot;
import com.ecommerce.order.client.ClientSnapshots.CatalogProductSnapshot;
import com.ecommerce.order.client.ClientSnapshots.ReservationSnapshot;
import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.dto.OrderLineResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderExceptions.CartEmptyException;
import com.ecommerce.order.exception.OrderExceptions.CartForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.CompensatedOrderException;
import com.ecommerce.order.exception.OrderExceptions.InsufficientStockException;
import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.exception.OrderExceptions.PaymentUnavailableException;
import com.ecommerce.order.exception.OrderExceptions.ProductNotFoundException;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.payment.PaymentGateway;
import com.ecommerce.order.service.OrderCommandService;
import com.ecommerce.order.service.PricedLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ports are mocked. Compensation is asserted in reverse, and only for steps
 * that completed: payment decline and payment outage release then unlock (no void);
 * insufficient stock only unlocks; a failure after commit also reverts and voids.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    private static final String TOKEN = "token";

    @Mock
    private CartClient cartClient;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private OrderCommandService orderCommandService;

    private OrderSagaOrchestrator orchestrator;

    private UUID userId;
    private UUID cartId;
    private UUID productId;
    private UUID orderId;
    private UUID reservationId;

    @BeforeEach
    void setUp() {
        orchestrator = new OrderSagaOrchestrator(
                cartClient, inventoryClient, catalogClient, paymentGateway, orderCommandService);
        userId = UUID.randomUUID();
        cartId = UUID.randomUUID();
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
    }

    @Test
    void happyPath_confirmsAfterLockReserveAuthorizeCommitClear() {
        stubPricedCart();
        when(inventoryClient.reserve(eq(orderId), any(), eq(TOKEN)))
                .thenReturn(new ReservationSnapshot(reservationId, orderId, "HELD"));
        when(orderCommandService.confirm(orderId)).thenReturn(response(OrderStatus.CONFIRMED, null));

        OrderResponse placed = orchestrator.place(userId, TOKEN, cartId, false);

        assertThat(placed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(placed.orderId()).isEqualTo(orderId);
        InOrder inOrder = inOrder(cartClient, inventoryClient, paymentGateway, orderCommandService);
        inOrder.verify(cartClient).lock(cartId, TOKEN);
        inOrder.verify(inventoryClient).reserve(eq(orderId), any(), eq(TOKEN));
        inOrder.verify(paymentGateway).authorize(eq(orderId), any(), eq(false), eq(TOKEN));
        inOrder.verify(inventoryClient).commit(reservationId, TOKEN);
        inOrder.verify(cartClient).clear(cartId, TOKEN);
        inOrder.verify(orderCommandService).confirm(orderId);
        verify(orderCommandService, never()).cancel(any(), any());
        verify(inventoryClient, never()).release(any(), any());
        verify(inventoryClient, never()).revert(any(), any());
        verify(cartClient, never()).unlock(any(), any());
        verify(paymentGateway, never()).voidAuthorization(any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PricedLine>> priced = ArgumentCaptor.forClass(List.class);
        verify(orderCommandService).createPending(eq(userId), eq(cartId), priced.capture());
        assertThat(priced.getValue()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(productId);
            assertThat(line.productName()).isEqualTo("Headphones");
            assertThat(line.unitPrice()).isEqualByComparingTo("9.99");
            assertThat(line.quantity()).isEqualTo(2);
        });
    }

    @Test
    void confirmFailureAfterClear_restoresThenRevertsThenVoids() {
        stubPricedCart();
        when(inventoryClient.reserve(eq(orderId), any(), eq(TOKEN)))
                .thenReturn(new ReservationSnapshot(reservationId, orderId, "HELD"));
        doThrow(new IllegalStateException("confirm failed")).when(orderCommandService).confirm(orderId);

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CompensatedOrderException.class)
                .satisfies(ex -> assertThat(((CompensatedOrderException) ex).getCode()).isEqualTo("SAGA_FAILED"));

        InOrder inOrder = inOrder(cartClient, inventoryClient, paymentGateway, orderCommandService);
        inOrder.verify(cartClient).clear(cartId, TOKEN);
        inOrder.verify(cartClient).restore(cartId, TOKEN);
        inOrder.verify(inventoryClient).revert(reservationId, TOKEN);
        inOrder.verify(paymentGateway).voidAuthorization(orderId, TOKEN);
        inOrder.verify(cartClient).unlock(cartId, TOKEN);
        inOrder.verify(orderCommandService).cancel(orderId, "SAGA_FAILED");
        verify(inventoryClient, never()).release(any(), any());
    }

    @Test
    void paymentDecline_releasesThenUnlocks_andDoesNotVoid() {
        stubPricedCart();
        when(inventoryClient.reserve(eq(orderId), any(), eq(TOKEN)))
                .thenReturn(new ReservationSnapshot(reservationId, orderId, "HELD"));
        doThrow(new PaymentDeclinedException("Payment was declined"))
                .when(paymentGateway).authorize(eq(orderId), any(), eq(true), eq(TOKEN));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, true))
                .isInstanceOf(CompensatedOrderException.class)
                .satisfies(ex -> assertThat(((CompensatedOrderException) ex).getCode()).isEqualTo("PAYMENT_DECLINED"));

        InOrder inOrder = inOrder(cartClient, inventoryClient, paymentGateway, orderCommandService);
        inOrder.verify(cartClient).lock(cartId, TOKEN);
        inOrder.verify(inventoryClient).reserve(eq(orderId), any(), eq(TOKEN));
        inOrder.verify(paymentGateway).authorize(eq(orderId), any(), eq(true), eq(TOKEN));
        inOrder.verify(inventoryClient).release(reservationId, TOKEN);
        inOrder.verify(cartClient).unlock(cartId, TOKEN);
        inOrder.verify(orderCommandService).cancel(orderId, "PAYMENT_DECLINED");
        verify(paymentGateway, never()).voidAuthorization(any(), any());
        verify(inventoryClient, never()).commit(any(), any());
        verify(inventoryClient, never()).revert(any(), any());
        verify(cartClient, never()).clear(any(), any());
        verify(cartClient, never()).restore(any(), any());
    }

    @Test
    void paymentOutage_releasesThenUnlocks_andDoesNotVoid() {
        stubPricedCart();
        when(inventoryClient.reserve(eq(orderId), any(), eq(TOKEN)))
                .thenReturn(new ReservationSnapshot(reservationId, orderId, "HELD"));
        doThrow(new PaymentUnavailableException("Payment service is unavailable"))
                .when(paymentGateway).authorize(eq(orderId), any(), eq(false), eq(TOKEN));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CompensatedOrderException.class)
                .satisfies(ex -> {
                    CompensatedOrderException compensated = (CompensatedOrderException) ex;
                    assertThat(compensated.getCode()).isEqualTo("PAYMENT_UNAVAILABLE");
                    assertThat(compensated.getMessage()).isEqualTo("Payment service is unavailable");
                });

        InOrder inOrder = inOrder(cartClient, inventoryClient, paymentGateway, orderCommandService);
        inOrder.verify(cartClient).lock(cartId, TOKEN);
        inOrder.verify(inventoryClient).reserve(eq(orderId), any(), eq(TOKEN));
        inOrder.verify(paymentGateway).authorize(eq(orderId), any(), eq(false), eq(TOKEN));
        inOrder.verify(inventoryClient).release(reservationId, TOKEN);
        inOrder.verify(cartClient).unlock(cartId, TOKEN);
        inOrder.verify(orderCommandService).cancel(orderId, "PAYMENT_UNAVAILABLE");
        verify(paymentGateway, never()).voidAuthorization(any(), any());
        verify(inventoryClient, never()).commit(any(), any());
        verify(inventoryClient, never()).revert(any(), any());
        verify(cartClient, never()).clear(any(), any());
        verify(cartClient, never()).restore(any(), any());
    }

    @Test
    void insufficientStock_unlocksOnly() {
        stubPricedCart();
        when(inventoryClient.reserve(eq(orderId), any(), eq(TOKEN)))
                .thenThrow(new InsufficientStockException("Insufficient stock to fill every line"));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CompensatedOrderException.class)
                .satisfies(ex -> assertThat(((CompensatedOrderException) ex).getCode()).isEqualTo("INSUFFICIENT_STOCK"));

        InOrder inOrder = inOrder(cartClient, inventoryClient, orderCommandService);
        inOrder.verify(cartClient).lock(cartId, TOKEN);
        inOrder.verify(inventoryClient).reserve(eq(orderId), any(), eq(TOKEN));
        inOrder.verify(cartClient).unlock(cartId, TOKEN);
        inOrder.verify(orderCommandService).cancel(orderId, "INSUFFICIENT_STOCK");
        verify(inventoryClient, never()).release(any(), any());
        verify(inventoryClient, never()).commit(any(), any());
        verify(paymentGateway, never()).authorize(any(), any(), anyBoolean(), any());
        verify(cartClient, never()).clear(any(), any());
    }

    @Test
    void failureAfterCommit_revertsVoidsAndUnlocks_andDoesNotRelease() {
        stubPricedCart();
        when(inventoryClient.reserve(eq(orderId), any(), eq(TOKEN)))
                .thenReturn(new ReservationSnapshot(reservationId, orderId, "HELD"));
        doThrow(new IllegalStateException("clear failed")).when(cartClient).clear(cartId, TOKEN);

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CompensatedOrderException.class)
                .satisfies(ex -> assertThat(((CompensatedOrderException) ex).getCode()).isEqualTo("SAGA_FAILED"));

        InOrder inOrder = inOrder(inventoryClient, paymentGateway, cartClient, orderCommandService);
        inOrder.verify(inventoryClient).commit(reservationId, TOKEN);
        inOrder.verify(inventoryClient).revert(reservationId, TOKEN);
        inOrder.verify(paymentGateway).voidAuthorization(orderId, TOKEN);
        inOrder.verify(cartClient).unlock(cartId, TOKEN);
        verify(inventoryClient, never()).release(any(), any());
        inOrder.verify(orderCommandService).cancel(orderId, "SAGA_FAILED");
        verify(cartClient, never()).restore(any(), any());
        verify(orderCommandService, never()).confirm(any());
    }

    @Test
    void unknownProduct_doesNotCreateAnOrder() {
        when(cartClient.getCart(cartId, TOKEN)).thenReturn(activeCart());
        when(catalogClient.getProduct(productId)).thenThrow(new ProductNotFoundException("Product not found"));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(ProductNotFoundException.class);

        verify(orderCommandService, never()).createPending(any(), any(), any());
        verify(cartClient, never()).lock(any(), any());
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void emptyCart_doesNotCreateAnOrder() {
        when(cartClient.getCart(cartId, TOKEN)).thenReturn(
                new CartSnapshot(cartId, userId, "ACTIVE", List.of()));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CartEmptyException.class);

        verifyNoInteractions(catalogClient);
        verify(orderCommandService, never()).createPending(any(), any(), any());
        verify(cartClient, never()).lock(any(), any());
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void cartOwnedBySomeoneElse_doesNotStartTheSaga() {
        when(cartClient.getCart(cartId, TOKEN)).thenReturn(
                new CartSnapshot(cartId, UUID.randomUUID(), "ACTIVE", List.of(new CartLineSnapshot(productId, 1))));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CartForbiddenException.class);

        verifyNoInteractions(catalogClient);
        verify(orderCommandService, never()).createPending(any(), any(), any());
        verify(cartClient, never()).lock(any(), any());
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void cartServiceForbidden_doesNotStartTheSaga() {
        when(cartClient.getCart(cartId, TOKEN)).thenThrow(new CartForbiddenException("Cart belongs to another user"));

        assertThatThrownBy(() -> orchestrator.place(userId, TOKEN, cartId, false))
                .isInstanceOf(CartForbiddenException.class);

        verify(orderCommandService, never()).createPending(any(), any(), any());
        verifyNoInteractions(catalogClient, inventoryClient);
    }

    private void stubPricedCart() {
        when(cartClient.getCart(cartId, TOKEN)).thenReturn(activeCart());
        when(catalogClient.getProduct(productId)).thenReturn(
                new CatalogProductSnapshot(UUID.randomUUID(), "Headphones", new BigDecimal("9.99")));
        when(orderCommandService.createPending(eq(userId), eq(cartId), any())).thenReturn(response(OrderStatus.PENDING, null));
    }

    private CartSnapshot activeCart() {
        return new CartSnapshot(cartId, userId, "ACTIVE", List.of(new CartLineSnapshot(productId, 2)));
    }

    private OrderResponse response(OrderStatus status, String failureCode) {
        return new OrderResponse(
                orderId,
                status,
                cartId,
                new BigDecimal("19.98"),
                List.of(new OrderLineResponse(productId, "Headphones", new BigDecimal("9.99"), 2, new BigDecimal("19.98"))),
                failureCode);
    }
}
