package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.HoldOutcome;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.dto.UpsertStockRequest;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.InventoryService.LineQuantity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Layered architecture, API tier. Stock upsert is open. Reservation commands
 * require the caller JWT (enforced by {@code SecurityConfig}) and return DTOs only.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/stock/{productId}")
    public ResponseEntity<StockResponse> upsert(@PathVariable UUID productId,
                                                 @Valid @RequestBody UpsertStockRequest request) {
        return ResponseEntity.ok(inventoryService.upsert(productId, request.available()));
    }

    @GetMapping("/stock/{productId}")
    public ResponseEntity<StockResponse> get(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryService.get(productId));
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        HoldOutcome outcome = inventoryService.hold(
                request.orderId(),
                request.lines().stream()
                        .map(line -> new LineQuantity(line.productId(), line.quantity()))
                        .toList());
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.reservation());
    }

    @PostMapping("/reservations/{id}/release")
    public ResponseEntity<ReservationResponse> release(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.release(id));
    }

    @PostMapping("/reservations/{id}/commit")
    public ResponseEntity<ReservationResponse> commit(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.commit(id));
    }

    @PostMapping("/reservations/{id}/revert")
    public ResponseEntity<ReservationResponse> revert(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.revert(id));
    }
}
