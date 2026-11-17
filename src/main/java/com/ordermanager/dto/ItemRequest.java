package com.ordermanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Payload de criação ou edição de um item
public record ItemRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name
) {
}