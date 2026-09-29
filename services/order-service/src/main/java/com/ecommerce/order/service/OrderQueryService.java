package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderExceptions.OrderForbiddenException;
import com.ecommerce.order.exception.OrderExceptions.OrderNotFoundException;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID orderId, UUID ownerId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if (!order.getOwnerId().equals(ownerId)) {
            throw new OrderForbiddenException("Order belongs to another user");
        }
        return OrderCommandService.toResponse(order);
    }
}
