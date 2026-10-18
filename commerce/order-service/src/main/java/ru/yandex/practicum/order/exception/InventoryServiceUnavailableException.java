package ru.yandex.practicum.order.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    private final Long productId;

    public InventoryServiceUnavailableException(Long productId, Throwable cause) {
        super("Сервис склада временно недоступен для товара " + productId, cause);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
