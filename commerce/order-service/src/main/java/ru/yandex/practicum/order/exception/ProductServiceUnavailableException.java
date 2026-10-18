package ru.yandex.practicum.order.exception;

public class ProductServiceUnavailableException extends RuntimeException {

    private final Long productId;

    public ProductServiceUnavailableException(Long productId, Throwable cause) {
        super("Сервис каталога временно недоступен для товара " + productId, cause);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
