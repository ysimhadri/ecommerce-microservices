package com.ecommerce.order.saga;

import com.ecommerce.order.client.CartClient;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.ClientSnapshots.CartLineSnapshot;
import com.ecommerce.order.client.ClientSnapshots.CartSnapshot;
import com.ecommerce.order.client.ClientSnapshots.CatalogProductSnapshot;
import com.ecommerce.order.client.ClientSnapshots.ReservationSnapshot;
import com.ecommerce.order.client.ClientSnapshots.ReserveLine;
import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderExceptions.CartEmptyException;
import com.ecommerce.order.exception.OrderExceptions.CartForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.CartNotActiveException;
import com.ecommerce.order.exception.OrderExceptions.CompensatedOrderException;
import com.ecommerce.order.exception.OrderExceptions.InsufficientStockException;
import com.ecommerce.order.exception.OrderExceptions.PaymentDeclinedException;
import com.ecommerce.order.payment.PaymentGateway;
import com.ecommerce.order.service.OrderCommandService;
import com.ecommerce.order.service.PricedLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-process orchestration. There is no broker, so a crash mid-request is
 * not replayed: a {@code PENDING} order and a {@code LOCKED} cart can remain.
 * Only steps that completed are compensated, in reverse. Creating the pending
 * order is local and is finished by setting {@code CANCELLED}, not by a remote call.
 *
 * <p>Forward: load cart, price from catalog, insert {@code PENDING}, lock,
 * reserve, authorize, commit, clear, confirm. A declined payment did not
 * complete authorize, so compensation is release then unlock. Insufficient
 * stock fails at reserve, so only unlock runs. Void runs only after authorize
 * succeeded and a later step failed. Commit replaces the hold, so a later
 * failure reverts and does not also release.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final CartClient cartClient;
    private final InventoryClient inventoryClient;
    private final CatalogClient catalogClient;
    private final PaymentGateway paymentGateway;
    private final OrderCommandService orderCommandService;

    public OrderSagaOrchestrator(CartClient cartClient,
                                  InventoryClient inventoryClient,
                                  CatalogClient catalogClient,
                                  PaymentGateway paymentGateway,
                                  OrderCommandService orderCommandService) {
        this.cartClient = cartClient;
        this.inventoryClient = inventoryClient;
        this.catalogClient = catalogClient;
        this.paymentGateway = paymentGateway;
        this.orderCommandService = orderCommandService;
    }

    public OrderResponse place(UUID userId, String bearerToken, UUID cartId, boolean simulatePaymentFailure) {
        CartSnapshot cart = cartClient.getCart(cartId, bearerToken);
        if (cart.ownerId() == null || !cart.ownerId().equals(userId)) {
            throw new CartForbiddenException("Cart belongs to another user");
        }
        if (!"ACTIVE".equals(cart.status())) {
            throw new CartNotActiveException("Cart is not active");
        }
        if (cart.lines() == null || cart.lines().isEmpty()) {
            throw new CartEmptyException("Cart has no lines");
        }

        List<PricedLine> priced = new ArrayList<>();
        for (CartLineSnapshot line : cart.lines()) {
            CatalogProductSnapshot product = catalogClient.getProduct(line.productId());
            priced.add(new PricedLine(line.productId(), product.name(), product.price(), line.quantity()));
        }

        OrderResponse pending = orderCommandService.createPending(userId, cartId, priced);
        List<Step> completed = new ArrayList<>();
        UUID reservationId = null;
        try {
            cartClient.lock(cartId, bearerToken);
            completed.add(Step.LOCK_CART);
            CartSnapshot locked = cartClient.getCart(cartId, bearerToken);
            if (locked == null || !sameLines(cart.lines(), locked.lines())) {
                throw new CartNotActiveException("Cart lines changed");
            }

            ReservationSnapshot reservation = inventoryClient.reserve(pending.orderId(), toReserveLines(priced), bearerToken);
            if (reservation == null || reservation.id() == null) {
                throw new IllegalStateException("Reservation id is missing");
            }
            reservationId = reservation.id();
            completed.add(Step.RESERVE);

            paymentGateway.authorize(pending.orderId(), pending.total(), simulatePaymentFailure);
            completed.add(Step.AUTHORIZE_PAYMENT);

            inventoryClient.commit(reservationId, bearerToken);
            // Commit supersedes the hold. Compensating both would release a
            // reservation that revert already restored.
            completed.remove(Step.RESERVE);
            completed.add(Step.COMMIT);

            cartClient.clear(cartId, bearerToken);
            completed.add(Step.CLEAR_CART);

            return orderCommandService.confirm(pending.orderId());
        } catch (RuntimeException ex) {
            String code = failureCode(ex);
            log.info("Order {} failed at saga step with {}; compensating {}", pending.orderId(), code, completed);
            try {
                compensate(completed, cartId, pending.orderId(), reservationId, bearerToken);
            } finally {
                orderCommandService.cancel(pending.orderId(), code);
            }
            throw new CompensatedOrderException(pending.orderId(), code, failureMessage(code));
        }
    }

    private void compensate(List<Step> completed, UUID cartId, UUID orderId, UUID reservationId, String bearerToken) {
        for (int i = completed.size() - 1; i >= 0; i--) {
            Step step = completed.get(i);
            try {
                log.info("Compensating {} for order {}", step, orderId);
                switch (step) {
                    case CLEAR_CART -> cartClient.restore(cartId, bearerToken);
                    case COMMIT -> inventoryClient.revert(reservationId, bearerToken);
                    case AUTHORIZE_PAYMENT -> paymentGateway.voidAuthorization(orderId);
                    case RESERVE -> inventoryClient.release(reservationId, bearerToken);
                    case LOCK_CART -> cartClient.unlock(cartId, bearerToken);
                }
            } catch (RuntimeException ex) {
                log.error("Compensation {} failed for order {}; continuing", step, orderId, ex);
            }
        }
    }

    private static boolean sameLines(List<CartLineSnapshot> left, List<CartLineSnapshot> right) {
        return lineCounts(left).equals(lineCounts(right));
    }

    private static Map<UUID, Integer> lineCounts(List<CartLineSnapshot> lines) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (lines == null) {
            return counts;
        }
        for (CartLineSnapshot line : lines) {
            counts.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return counts;
    }

    private static List<ReserveLine> toReserveLines(List<PricedLine> priced) {
        return priced.stream()
                .map(line -> new ReserveLine(line.productId(), line.quantity()))
                .toList();
    }

    private static String failureCode(RuntimeException ex) {
        if (ex instanceof PaymentDeclinedException) {
            return "PAYMENT_DECLINED";
        }
        if (ex instanceof InsufficientStockException) {
            return "INSUFFICIENT_STOCK";
        }
        if (ex instanceof CartNotActiveException) {
            return "CART_NOT_ACTIVE";
        }
        return "SAGA_FAILED";
    }

    private static String failureMessage(String code) {
        return switch (code) {
            case "PAYMENT_DECLINED" -> "Payment was declined";
            case "INSUFFICIENT_STOCK" -> "Insufficient stock to fill every line";
            case "CART_NOT_ACTIVE" -> "Cart is not active";
            default -> "Order placement failed";
        };
    }

    private enum Step {
        LOCK_CART,
        RESERVE,
        AUTHORIZE_PAYMENT,
        COMMIT,
        CLEAR_CART
    }
}
