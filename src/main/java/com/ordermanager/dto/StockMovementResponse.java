package com.ordermanager.dto;

import java.time.Instant;

import com.ordermanager.domain.StockMovement;

// Payload de retorno para um movimento de stock
public record StockMovementResponse(
        Long id,
        Long itemId,
        String itemName,
        long quantity,
        long allocatedQuantity,
        long availableQuantity,
        Instant createAt

) {


    public static StockMovementResponse from(StockMovement movement, long allocated) {
        long available = Math.max(0, movement.getQuantity() - allocated);
        return new StockMovementResponse(
                movement.getId(),
                movement.getItem().getId(),
                movement.getItem().getName(),
                movement.getQuantity(),
                allocated,
                available,
                movement.getCreatedAt());
    }

}