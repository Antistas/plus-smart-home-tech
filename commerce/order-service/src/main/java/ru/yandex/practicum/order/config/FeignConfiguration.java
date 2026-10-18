package ru.yandex.practicum.order.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Configuration
public class FeignConfiguration {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> template.header(REQUEST_ID_HEADER, resolveRequestId());
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
