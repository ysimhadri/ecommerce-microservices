package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderLineResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderExceptions.OrderNotFoundException;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderLine;
import com.ecommerce.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Local order transactions. Each method commits on its own. The saga is not
 * one XA transaction across cart, inventory, and this database.
 * Cancel keeps the row and its lines.
 */
@Service
public class OrderCommandService {

    private final OrderRepository orderRepository;

    public OrderCommandService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createPending(UUID ownerId, UUID cartId, List<PricedLine> lines) {
        CustomerOrder order = new CustomerOrder(ownerId, cartId);
        for (PricedLine line : lines) {
            order.addLine(new OrderLine(line.productId(), line.productName(), line.unitPrice(), line.quantity()));
        }
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse confirm(UUID orderId) {
        CustomerOrder order = require(orderId);
        order.confirm();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId, String failureCode) {
        CustomerOrder order = require(orderId);
        order.cancel(failureCode);
        return toResponse(order);
    }

    private CustomerOrder require(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }

    static OrderResponse toResponse(CustomerOrder order) {
        List<OrderLineResponse> lines = order.getLines().stream()
                .map(line -> new OrderLineResponse(
                        line.getProductId(),
                        line.getProductName(),
                        line.getUnitPrice(),
                        line.getQuantity(),
                        line.getLineTotal()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCartId(),
                order.getTotal(),
                lines,
                order.getFailureCode());
    }
}
