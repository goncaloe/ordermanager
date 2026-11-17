package com.ordermanager.dto;

import com.ordermanager.domain.Item;

import java.time.Instant;

// Payload de retorno para um item
public record ItemResponse(Long id, String name, Instant createdAt) {

    // Helper para converter Entity para DTO
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getCreatedAt());
    }
}