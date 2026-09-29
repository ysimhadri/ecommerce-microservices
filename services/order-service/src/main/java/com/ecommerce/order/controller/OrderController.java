package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PlaceOrderRequest;
import com.ecommerce.order.saga.OrderSagaOrchestrator;
import com.ecommerce.order.security.BearerTokens;
import com.ecommerce.order.security.CurrentUser;
import com.ecommerce.order.service.OrderQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Layered architecture, API tier. Place delegates to the saga; get delegates
 * to the query service. Controllers return DTOs only.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderSagaOrchestrator orderSagaOrchestrator, OrderQueryService orderQueryService) {
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request,
                                                HttpServletRequest httpRequest) {
        OrderResponse created = orderSagaOrchestrator.place(
                CurrentUser.id(),
                BearerTokens.require(httpRequest),
                request.cartId(),
                request.simulatePaymentFailureOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(orderQueryService.get(id, CurrentUser.id()));
    }
}
