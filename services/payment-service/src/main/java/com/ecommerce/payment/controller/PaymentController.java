package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.AuthorizeOutcome;
import com.ecommerce.payment.dto.AuthorizePaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.security.CurrentUser;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Layered architecture, API tier. Authorize and void return DTOs only.
 * A missing void is 200 with no body and does not insert a row.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorizations")
    public ResponseEntity<PaymentResponse> authorize(@Valid @RequestBody AuthorizePaymentRequest request) {
        AuthorizeOutcome outcome = paymentService.authorize(
                CurrentUser.id(),
                request.orderId(),
                request.amount(),
                request.currency(),
                Boolean.TRUE.equals(request.simulateDecline()),
                Boolean.TRUE.equals(request.simulateOutage()));
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.payment());
    }

    @PostMapping("/authorizations/{orderId}/void")
    public ResponseEntity<PaymentResponse> voidAuthorization(@PathVariable UUID orderId) {
        return paymentService.voidAuthorization(CurrentUser.id(), orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build());
    }
}
