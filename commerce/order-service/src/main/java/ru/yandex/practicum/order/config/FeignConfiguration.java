package ru.yandex.practicum.order.config;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.yandex.practicum.order.exception.OrderProcessingException;

import java.util.UUID;

@Configuration
public class FeignConfiguration {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> template.header(REQUEST_ID_HEADER, resolveRequestId());
    }

    @Bean
    public ErrorDecoder businessErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            if (response.status() == 404) {
                String message = methodKey.contains("ProductClient")
                        ? "Товар не найден"
                        : "Складская запись не найдена";
                return new OrderProcessingException(message);
            }
            if (response.status() == 409) {
                return new OrderProcessingException("Недостаточно товара для оформления заказа");
            }
            if (response.status() >= 400 && response.status() < 500) {
                return new OrderProcessingException("Соседний сервис отклонил операцию заказа");
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }

    private String resolveRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String requestId = servletAttributes.getRequest().getHeader(REQUEST_ID_HEADER);
            if (requestId != null && !requestId.isBlank()) {
                return requestId;
            }
        }
        return UUID.randomUUID().toString();
    }
}
