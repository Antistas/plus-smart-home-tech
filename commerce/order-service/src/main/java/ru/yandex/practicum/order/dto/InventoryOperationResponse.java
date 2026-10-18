package ru.yandex.practicum.order.dto;

public record InventoryOperationResponse(
        boolean success,
        Integer availableQuantity,
        String message
) {
}
