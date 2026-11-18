package com.ordermanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public record OrderRequest(
        @NotNull(message = "itemId is required") Long itemId,
        @NotNull(message = "userId is required") Long userId,
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1") Long quantity
) { }