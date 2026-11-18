package com.ordermanager.dto;

import com.ordermanager.repository.OrderRefProjection;

import java.time.Instant;

// referência para uma order a partir de um movement
public record OrderRef(
        Long id,
        Long userId,
        long quantity,
        Instant createdAt,
        boolean complete
) {

    public static OrderRef from(OrderRefProjection projection) {
        return new OrderRef(
                projection.getId(),
                projection.getUserId(),
                projection.getQuantity(),
                projection.getCreatedAt(),
                projection.getFulfilledQuantity() >= projection.getQuantity());
    }
}