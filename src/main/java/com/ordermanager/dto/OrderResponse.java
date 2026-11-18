package com.ordermanager.dto;

import java.time.Instant;
import java.util.List;

import com.ordermanager.domain.Order;

public record OrderResponse(
        Long id,
        Long itemId,
        String itemName,
        Long userId,
        String userName,
        String userEmail,
        long quantity,
        Instant createdAt,
        List<OrderMovementResponse> allocations
) {

    public static OrderResponse from(Order order) {
        List<OrderMovementResponse> allocations = order.getAllocations().stream()
                .map(OrderMovementResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getItem().getId(),
                order.getItem().getName(),
                order.getUser().getId(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                order.getQuantity(),
                order.getCreatedAt(),
                allocations);
    }
}