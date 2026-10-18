package ru.yandex.practicum.order.client;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;

@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return productId -> {
            OrderProcessingException businessError = findCause(cause, OrderProcessingException.class);
            if (businessError != null) {
                throw businessError;
            }
            FeignException feignException = findFeignException(cause);
            if (feignException != null && feignException.status() == 404) {
                throw new OrderProcessingException("Товар " + productId + " не найден", cause);
            }
            if (feignException != null && isBusinessError(feignException.status())) {
                throw new OrderProcessingException(
                        "Каталог отклонил запрос для товара " + productId, cause
                );
            }

            log.warn("Сбой product-service при получении товара {}. Причина: {}",
                    productId, cause.toString());
            throw new ProductServiceUnavailableException(productId, cause);
        };
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
