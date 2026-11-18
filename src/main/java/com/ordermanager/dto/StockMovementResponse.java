package com.ordermanager.dto;

import java.time.Instant;

import com.ordermanager.domain.StockMovement;

// Payload de retorno para um movimento de stock
public record StockMovementResponse(
        Long id,
        Long itemId,
        String itemName,
        long quantity,
        Instant createAt

        // TODO: incluir quantidade alocada
) {


    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getItem().getId(),
                movement.getItem().getName(),
                movement.getQuantity(),
                movement.getCreatedAt());
    }

}