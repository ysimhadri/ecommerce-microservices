package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.HoldOutcome;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.exception.InventoryExceptions.InsufficientStockException;
import com.ecommerce.inventory.exception.InventoryExceptions.InvalidStockRequestException;
import com.ecommerce.inventory.model.Reservation;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.model.Stock;
import com.ecommerce.inventory.repository.ReservationRepository;
import com.ecommerce.inventory.repository.StockRepository;
import com.ecommerce.inventory.service.InventoryService.LineQuantity;
import jakarta.persistence.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hold, insufficient stock (all-or-nothing), release, commit, revert, and
 * the idempotent replay of the same order id. Persistence is mocked; the
 * stock objects are real so the counts can be asserted directly.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID productId;
    private Stock stock;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        stock = new Stock(productId, 10);
    }

    @Test
    void upsert_setsAvailableAndRejectsNegative() {
        when(stockRepository.findById(productId)).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockResponse created = inventoryService.upsert(productId, 7);

        assertThat(created.productId()).isEqualTo(productId);
        assertThat(created.available()).isEqualTo(7);
        assertThat(created.reserved()).isZero();
        assertThatThrownBy(() -> inventoryService.upsert(productId, -1))
                .isInstanceOf(InvalidStockRequestException.class);
    }

    @Test
    void stockRow_usesOptimisticLockingVersion() throws Exception {
        assertThat(Stock.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
    }

    @Test
    void hold_movesAvailableIntoReserved() {
        stubStock();
        stubNewReservation();

        HoldOutcome outcome = inventoryService.hold(UUID.randomUUID(), List.of(new LineQuantity(productId, 4)));

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.reservation().status()).isEqualTo(ReservationStatus.HELD);
        assertThat(stock.getAvailable()).isEqualTo(6);
        assertThat(stock.getReserved()).isEqualTo(4);
    }

    @Test
    void hold_sameOrderId_doesNotHoldTwice() {
        stubStock();
        UUID orderId = UUID.randomUUID();
        stubNewReservation();

        HoldOutcome first = inventoryService.hold(orderId, List.of(new LineQuantity(productId, 4)));
        HoldOutcome second = inventoryService.hold(orderId, List.of(new LineQuantity(productId, 4)));

        assertThat(second.created()).isFalse();
        assertThat(second.reservation().id()).isEqualTo(first.reservation().id());
        assertThat(stock.getAvailable()).isEqualTo(6);
        assertThat(stock.getReserved()).isEqualTo(4);
    }

    @Test
    void hold_whenAnyLineIsShort_changesNothing() {
        UUID otherId = UUID.randomUUID();
        Stock other = new Stock(otherId, 1);
        when(stockRepository.findById(productId)).thenReturn(Optional.of(stock));
        when(stockRepository.findById(otherId)).thenReturn(Optional.of(other));
        when(reservationRepository.findByOrderId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.hold(UUID.randomUUID(), List.of(
                new LineQuantity(productId, 3),
                new LineQuantity(otherId, 2))))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();
        assertThat(other.getAvailable()).isEqualTo(1);
        assertThat(other.getReserved()).isZero();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void hold_whenStockRowIsMissing_isInsufficientAndChangesNothing() {
        when(reservationRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(stockRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.hold(UUID.randomUUID(), List.of(new LineQuantity(productId, 1))))
                .isInstanceOf(InsufficientStockException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void release_restoresAvailable_andIsIdempotent() {
        ReservationResponse held = holdFour();

        ReservationResponse released = inventoryService.release(held.id());
        ReservationResponse again = inventoryService.release(held.id());

        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(again.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();
    }

    @Test
    void commit_dropsReservedAndLeavesAvailableDown_revertRestoresAvailableOnce() {
        ReservationResponse held = holdFour();

        ReservationResponse committed = inventoryService.commit(held.id());
        assertThat(committed.status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(stock.getAvailable()).isEqualTo(6);
        assertThat(stock.getReserved()).isZero();

        inventoryService.commit(held.id());
        assertThat(stock.getAvailable()).isEqualTo(6);

        ReservationResponse reverted = inventoryService.revert(held.id());
        inventoryService.revert(held.id());

        assertThat(reverted.status()).isEqualTo(ReservationStatus.REVERTED);
        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();

        ReservationResponse releasedAfterRevert = inventoryService.release(held.id());
        assertThat(releasedAfterRevert.status()).isEqualTo(ReservationStatus.REVERTED);
        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();
    }

    private ReservationResponse holdFour() {
        stubStock();
        stubNewReservation();
        return inventoryService.hold(UUID.randomUUID(), List.of(new LineQuantity(productId, 4))).reservation();
    }

    private void stubStock() {
        when(stockRepository.findById(productId)).thenReturn(Optional.of(stock));
    }

    private void stubNewReservation() {
        AtomicReference<Reservation> stored = new AtomicReference<>();
        when(reservationRepository.findByOrderId(any())).thenAnswer(invocation -> {
            Reservation reservation = stored.get();
            if (reservation != null && reservation.getOrderId().equals(invocation.getArgument(0))) {
                return Optional.of(reservation);
            }
            return Optional.empty();
        });
        lenient().when(reservationRepository.findById(any())).thenAnswer(invocation -> {
            Reservation reservation = stored.get();
            if (reservation != null && reservation.getId().equals(invocation.getArgument(0))) {
                return Optional.of(reservation);
            }
            return Optional.empty();
        });
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            stored.set(saved);
            return saved;
        });
    }
}
