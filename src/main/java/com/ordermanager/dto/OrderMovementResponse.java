package com.ordermanager.dto;

import java.time.Instant;

import com.ordermanager.domain.OrderMovement;

// Cada registo corresponde a um movimento da order
public record OrderMovementResponse(
        Long stockMovementId,
        long quantity,
        Instant stockMovementCreatedAt
) {

    public static OrderMovementResponse from(OrderMovement mov) {
        return new OrderMovementResponse(
                mov.getStockMovement().getId(),
                mov.getQuantity(),
                mov.getStockMovement().getCreatedAt());
    }
}