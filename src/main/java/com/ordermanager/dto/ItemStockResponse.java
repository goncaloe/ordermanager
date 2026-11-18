package com.ordermanager.dto;

public record ItemStockResponse(Long itemId, String itemName, long availableQuantity) {
}