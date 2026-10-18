package ru.yandex.practicum.order.client;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.dto.InventoryOperationRequest;
import ru.yandex.practicum.order.dto.InventoryOperationResponse;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.OrderProcessingException;

@Slf4j
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {
            @Override
            public InventoryOperationResponse reserve(InventoryOperationRequest request) {
                throw classify(request.productId(), "резервирование", cause);
            }

            @Override
            public InventoryOperationResponse release(InventoryOperationRequest request) {
                throw classify(request.productId(), "снятие резерва", cause);
            }
        };
    }

    private RuntimeException classify(Long productId, String operation, Throwable cause) {
        OrderProcessingException businessError = findCause(cause, OrderProcessingException.class);
        if (businessError != null) {
            return businessError;
        }
        FeignException feignException = findFeignException(cause);
        if (feignException != null && feignException.status() == 404) {
            return new OrderProcessingException(
                    "Складская запись для товара " + productId + " не найдена", cause
            );
        }
        if (feignException != null && feignException.status() == 409) {
            return new OrderProcessingException(
                    "Недостаточно товара " + productId + " для оформления заказа", cause
            );
        }
        if (feignException != null && isBusinessError(feignException.status())) {
            return new OrderProcessingException(
                    "Склад отклонил операцию для товара " + productId, cause
            );
        }

        log.warn("Сбой inventory-service при операции '{}' для товара {}. Причина: {}",
                operation, productId, cause.toString());
        return new InventoryServiceUnavailableException(productId, cause);
    }

    private <T extends Throwable> T findCause(Throwable cause, Class<T> type) {
        Throwable current = cause;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isBusinessError(int status) {
        return status >= 400 && status < 500;
    }

    private FeignException findFeignException(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof FeignException feignException) {
                return feignException;
            }
            current = current.getCause();
        }
        return null;
    }
}
