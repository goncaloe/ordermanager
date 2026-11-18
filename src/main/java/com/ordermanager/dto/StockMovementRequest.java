package com.ordermanager.dto;

import jakarta.validation.constraints.NotNull;

// Payload para registar stock dos items
public record StockMovementRequest(
        @NotNull(message = "itemId is required") Long itemId,

        //positivo = bens recebidos; negativo = bens devolvidos do stock
        //apenas positivo podem completar encomendas
        @NotNull(message = "quantity is required") Long quantity
) {
}