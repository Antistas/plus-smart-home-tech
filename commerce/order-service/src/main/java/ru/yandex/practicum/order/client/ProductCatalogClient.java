package ru.yandex.practicum.order.client;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.yandex.practicum.order.exception.NotFoundException;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ProductCatalogClient {

    private static final String PRODUCT_SERVICE = "product-service";

    private final DiscoveryClient discoveryClient;
    private final RestClient.Builder restClientBuilder;

    public ProductSnapshot getProduct(Long productId) {
        ServiceInstance instance = discoveryClient.getInstances(PRODUCT_SERVICE).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Сервис каталога товаров недоступен"));

        ProductSnapshot product = restClientBuilder.baseUrl(instance.getUri().toString())
                .build()
                .get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(ProductSnapshot.class);

        if (product == null) {
            throw new NotFoundException("Товар с id " + productId + " не найден");
        }
        return product;
    }

    public record ProductSnapshot(Long id, String name, BigDecimal price) {
    }
}
