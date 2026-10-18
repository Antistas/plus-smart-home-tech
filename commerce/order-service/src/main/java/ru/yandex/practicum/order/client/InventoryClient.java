package ru.yandex.practicum.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.order.dto.InventoryOperationRequest;
import ru.yandex.practicum.order.dto.InventoryOperationResponse;

@FeignClient(
        name = "inventory-service",
        fallbackFactory = InventoryClientFallbackFactory.class
)
public interface InventoryClient {

    @PostMapping("/api/inventory/reserve")
    InventoryOperationResponse reserve(@RequestBody InventoryOperationRequest request);

    @PostMapping("/api/inventory/release")
    InventoryOperationResponse release(@RequestBody InventoryOperationRequest request);
}
