package ru.yandex.practicum.order.dto;

public record InventoryOperationRequest(Long productId, Integer quantity) {
}
