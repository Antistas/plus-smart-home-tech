package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.client.InventoryClient;
import ru.yandex.practicum.order.client.ProductClient;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.InventoryOperationRequest;
import ru.yandex.practicum.order.dto.InventoryOperationResponse;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.dto.ProductResponse;
import ru.yandex.practicum.order.entity.OrderStatus;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String PRODUCT_DEGRADED_DETAILS =
            "Заказ требует ручной проверки: каталог товаров временно недоступен";
    private static final String INVENTORY_DEGRADED_DETAILS =
            "Заказ требует ручной проверки: резервирование на складе не подтверждено";

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderPersistenceService orderPersistenceService;

    public OrderDto create(CreateOrderRequest request) {
        // Обратная совместимость с контрактом прошлого спринта.
        if (request.items().stream().allMatch(this::containsLegacySnapshot)) {
            return orderPersistenceService.save(request, false);
        }

        Map<Long, Integer> quantities = aggregateQuantities(request.items());
        ServiceCallResult<Map<Long, ProductResponse>> productResult = loadProducts(quantities.keySet());
        rejectBusinessFailure(productResult);

        List<OrderItemRequest> snapshotItems = buildSnapshotItems(request.items(), productResult.value());
        CreateOrderRequest snapshotRequest = new CreateOrderRequest(
                request.customerName(), request.customerEmail(), snapshotItems
        );

        if (productResult.isDegraded()) {
            return orderPersistenceService.save(
                    snapshotRequest, OrderStatus.PENDING_CONFIRMATION, productResult.message()
            );
        }

        return reserveAndSave(snapshotRequest, quantities);
    }

    private OrderDto reserveAndSave(CreateOrderRequest snapshotRequest, Map<Long, Integer> quantities) {
        List<InventoryOperationRequest> completedReservations = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            InventoryOperationRequest reservation =
                    new InventoryOperationRequest(entry.getKey(), entry.getValue());
            ServiceCallResult<InventoryOperationResponse> result = reserve(reservation);

            if (result.isFailure()) {
                releaseReservations(completedReservations);
                throw new OrderProcessingException(result.message());
            }
            if (result.isDegraded()) {
                releaseReservations(completedReservations);
                return orderPersistenceService.save(
                        snapshotRequest, OrderStatus.PENDING_CONFIRMATION, result.message()
                );
            }
            completedReservations.add(reservation);
        }

        try {
            return orderPersistenceService.save(snapshotRequest, OrderStatus.CONFIRMED, null);
        } catch (RuntimeException exception) {
            releaseReservations(completedReservations);
            throw new OrderProcessingException("Не удалось завершить создание заказа", exception);
        }
    }

    private ServiceCallResult<Map<Long, ProductResponse>> loadProducts(Iterable<Long> productIds) {
        Map<Long, ProductResponse> products = new LinkedHashMap<>();
        boolean degraded = false;

        for (Long productId : productIds) {
            ServiceCallResult<ProductResponse> result = getProduct(productId);
            if (result.isFailure()) {
                return ServiceCallResult.failure(result.message());
            }
            if (result.isDegraded()) {
                degraded = true;
            }
            products.put(productId, result.value());
        }

        return degraded
                ? ServiceCallResult.degraded(products, PRODUCT_DEGRADED_DETAILS)
                : ServiceCallResult.success(products);
    }

    private ServiceCallResult<ProductResponse> getProduct(Long productId) {
        try {
            ProductResponse product = productClient.getProduct(productId);
            if (!Boolean.TRUE.equals(product.active())) {
                return ServiceCallResult.failure("Товар " + productId + " снят с продажи");
            }
            return ServiceCallResult.success(product);
        } catch (OrderProcessingException exception) {
            return ServiceCallResult.failure(exception.getMessage());
        } catch (ProductServiceUnavailableException exception) {
            return degradedProduct(productId);
        } catch (FeignException exception) {
            return classifyProductFeignFailure(productId, exception);
        } catch (RuntimeException exception) {
            if (findCause(exception, OrderProcessingException.class) != null) {
                return ServiceCallResult.failure(
                        findCause(exception, OrderProcessingException.class).getMessage()
                );
            }
            return degradedProduct(productId);
        }
    }

    private ServiceCallResult<ProductResponse> classifyProductFeignFailure(
            Long productId,
            FeignException exception
    ) {
        if (exception.status() == 404) {
            return ServiceCallResult.failure("Товар " + productId + " не найден");
        }
        if (exception.status() >= 400 && exception.status() < 500) {
            return ServiceCallResult.failure("Каталог отклонил запрос для товара " + productId);
        }
        return degradedProduct(productId);
    }

    private ServiceCallResult<ProductResponse> degradedProduct(Long productId) {
        ProductResponse placeholder = new ProductResponse(
                productId,
                "Товар #" + productId + " (ожидает проверки)",
                BigDecimal.ZERO,
                true
        );
        return ServiceCallResult.degraded(placeholder, PRODUCT_DEGRADED_DETAILS);
    }

    private ServiceCallResult<InventoryOperationResponse> reserve(InventoryOperationRequest request) {
        try {
            return ServiceCallResult.success(inventoryClient.reserve(request));
        } catch (OrderProcessingException exception) {
            return ServiceCallResult.failure(exception.getMessage());
        } catch (InventoryServiceUnavailableException exception) {
            return ServiceCallResult.degraded(null, INVENTORY_DEGRADED_DETAILS);
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                return ServiceCallResult.failure(
                        "Складская запись для товара " + request.productId() + " не найдена"
                );
            }
            if (exception.status() == 409) {
                return ServiceCallResult.failure(
                        "Недостаточно товара " + request.productId() + " для оформления заказа"
                );
            }
            if (exception.status() >= 400 && exception.status() < 500) {
                return ServiceCallResult.failure(
                        "Склад отклонил операцию для товара " + request.productId()
                );
            }
            return ServiceCallResult.degraded(null, INVENTORY_DEGRADED_DETAILS);
        } catch (RuntimeException exception) {
            OrderProcessingException businessError = findCause(exception, OrderProcessingException.class);
            if (businessError != null) {
                return ServiceCallResult.failure(businessError.getMessage());
            }
            return ServiceCallResult.degraded(null, INVENTORY_DEGRADED_DETAILS);
        }
    }

    private void releaseReservations(List<InventoryOperationRequest> reservations) {
        for (int index = reservations.size() - 1; index >= 0; index--) {
            InventoryOperationRequest reservation = reservations.get(index);
            try {
                inventoryClient.release(reservation);
            } catch (Exception compensationError) {
                log.error("Не удалось снять резерв товара {} в количестве {}",
                        reservation.productId(), reservation.quantity(), compensationError);
            }
        }
    }

    private List<OrderItemRequest> buildSnapshotItems(
            List<OrderItemRequest> items,
            Map<Long, ProductResponse> products
    ) {
        return items.stream()
                .map(item -> toSnapshot(item, products.get(item.productId())))
                .toList();
    }

    private Map<Long, Integer> aggregateQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        return quantities;
    }

    private OrderItemRequest toSnapshot(OrderItemRequest item, ProductResponse product) {
        return new OrderItemRequest(item.productId(), product.name(), item.quantity(), product.price());
    }

    private <T> void rejectBusinessFailure(ServiceCallResult<T> result) {
        if (result.isFailure()) {
            throw new OrderProcessingException(result.message());
        }
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean containsLegacySnapshot(OrderItemRequest item) {
        return item.productName() != null && item.price() != null;
    }

    @Transactional(readOnly = true)
    public OrderDto getById(Long id) {
        return orderMapper.toDto(orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заказ с id " + id + " не найден")));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getByEmail(String email) {
        return orderRepository.findAllByCustomerEmail(email).stream()
                .map(orderMapper::toDto)
                .toList();
    }
}
