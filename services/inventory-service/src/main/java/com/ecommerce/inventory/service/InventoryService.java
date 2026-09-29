package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.HoldOutcome;
import com.ecommerce.inventory.dto.ReservationLineResponse;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.exception.InventoryExceptions.InsufficientStockException;
import com.ecommerce.inventory.exception.InventoryExceptions.InvalidStockRequestException;
import com.ecommerce.inventory.exception.InventoryExceptions.ReservationNotFoundException;
import com.ecommerce.inventory.exception.InventoryExceptions.ReservationStateException;
import com.ecommerce.inventory.exception.InventoryExceptions.StockNotFoundException;
import com.ecommerce.inventory.model.Reservation;
import com.ecommerce.inventory.model.ReservationLine;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.model.Stock;
import com.ecommerce.inventory.repository.ReservationRepository;
import com.ecommerce.inventory.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each public method is its own local transaction. A hold either reserves
 * every line or changes nothing. Replaying the same {@code orderId} returns
 * the existing reservation and does not move stock again. Compensations
 * (release, revert) are idempotent.
 */
@Service
public class InventoryService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    public InventoryService(StockRepository stockRepository, ReservationRepository reservationRepository) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public StockResponse upsert(UUID productId, int available) {
        if (available < 0) {
            throw new InvalidStockRequestException("available: must not be negative");
        }
        Stock stock = stockRepository.findById(productId).orElse(null);
        if (stock == null) {
            stock = new Stock(productId, available);
        } else {
            stock.setAvailable(available);
        }
        return toStock(stockRepository.save(stock));
    }

    @Transactional(readOnly = true)
    public StockResponse get(UUID productId) {
        return toStock(stockRepository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException("Stock not found")));
    }

    @Transactional
    public HoldOutcome hold(UUID orderId, List<LineQuantity> requestedLines) {
        if (orderId == null) {
            throw new InvalidStockRequestException("orderId: must not be null");
        }
        Reservation existing = reservationRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return new HoldOutcome(toReservation(existing), false);
        }
        List<LineQuantity> lines = merge(requestedLines);
        List<Stock> stocks = new ArrayList<>();
        for (LineQuantity line : lines) {
            Stock stock = stockRepository.findById(line.productId()).orElse(null);
            if (stock == null || stock.getAvailable() < line.quantity()) {
                throw new InsufficientStockException("Insufficient stock to fill every line");
            }
            stocks.add(stock);
        }
        Reservation reservation = new Reservation(orderId);
        for (int i = 0; i < lines.size(); i++) {
            LineQuantity line = lines.get(i);
            stocks.get(i).hold(line.quantity());
            reservation.addLine(new ReservationLine(line.productId(), line.quantity()));
        }
        return new HoldOutcome(toReservation(reservationRepository.save(reservation)), true);
    }

    /**
     * {@code HELD} → {@code RELEASED}, available restored. A second release does not
     * move stock. {@code REVERTED} is also a no-op: revert already restored available.
     */
    @Transactional
    public ReservationResponse release(UUID reservationId) {
        Reservation reservation = requireReservation(reservationId);
        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.REVERTED) {
            return toReservation(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new ReservationStateException("Reservation cannot be released from " + reservation.getStatus());
        }
        for (ReservationLine line : reservation.getLines()) {
            requireStock(line.getProductId()).release(line.getQuantity());
        }
        reservation.markReleased();
        return toReservation(reservation);
    }

    /** {@code HELD} → {@code COMMITTED}. Reserved drops; available stays down. */
    @Transactional
    public ReservationResponse commit(UUID reservationId) {
        Reservation reservation = requireReservation(reservationId);
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            return toReservation(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new ReservationStateException("Reservation cannot be committed from " + reservation.getStatus());
        }
        for (ReservationLine line : reservation.getLines()) {
            requireStock(line.getProductId()).commit(line.getQuantity());
        }
        reservation.markCommitted();
        return toReservation(reservation);
    }

    /** Compensation for commit: available restored. A second revert does not move stock. */
    @Transactional
    public ReservationResponse revert(UUID reservationId) {
        Reservation reservation = requireReservation(reservationId);
        if (reservation.getStatus() == ReservationStatus.REVERTED) {
            return toReservation(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.COMMITTED) {
            throw new ReservationStateException("Reservation cannot be reverted from " + reservation.getStatus());
        }
        for (ReservationLine line : reservation.getLines()) {
            requireStock(line.getProductId()).revert(line.getQuantity());
        }
        reservation.markReverted();
        return toReservation(reservation);
    }

    private List<LineQuantity> merge(List<LineQuantity> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new InvalidStockRequestException("lines: must not be empty");
        }
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (LineQuantity line : requestedLines) {
            if (line.productId() == null) {
                throw new InvalidStockRequestException("productId: must not be null");
            }
            if (line.quantity() < 1) {
                throw new InvalidStockRequestException("quantity: must be at least 1");
            }
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return quantities.entrySet().stream()
                .map(entry -> new LineQuantity(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Reservation requireReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found"));
    }

    private Stock requireStock(UUID productId) {
        return stockRepository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException("Stock not found"));
    }

    private StockResponse toStock(Stock stock) {
        return new StockResponse(stock.getProductId(), stock.getAvailable(), stock.getReserved());
    }

    private ReservationResponse toReservation(Reservation reservation) {
        List<ReservationLineResponse> lines = reservation.getLines().stream()
                .map(line -> new ReservationLineResponse(line.getProductId(), line.getQuantity()))
                .toList();
        return new ReservationResponse(reservation.getId(), reservation.getOrderId(), reservation.getStatus(), lines);
    }

    public record LineQuantity(UUID productId, int quantity) {
    }
}
