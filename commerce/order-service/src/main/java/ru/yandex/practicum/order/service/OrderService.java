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
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.dto.ProductResponse;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderPersistenceService orderPersistenceService;

    public OrderDto create(CreateOrderRequest request) {
        // Поддержка контракта прошлого спринта. Новый контракт передаёт только productId и quantity.
        if (request.items().stream().allMatch(this::containsLegacySnapshot)) {
            return orderPersistenceService.save(request, false);
        }

        Map<Long, Integer> quantities = aggregateQuantities(request.items());
        Map<Long, ProductResponse> products = loadProducts(quantities.keySet());
        List<InventoryOperationRequest> completedReservations = new ArrayList<>();

        try {
            for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
                InventoryOperationRequest reservation =
                        new InventoryOperationRequest(entry.getKey(), entry.getValue());
                reserve(reservation);
                completedReservations.add(reservation);
            }

            List<OrderItemRequest> snapshotItems = request.items().stream()
                    .map(item -> toSnapshot(item, products.get(item.productId())))
                    .toList();
            CreateOrderRequest snapshotRequest = new CreateOrderRequest(
                    request.customerName(), request.customerEmail(), snapshotItems
            );

            return orderPersistenceService.save(snapshotRequest, true);
        } catch (RuntimeException exception) {
            releaseReservations(completedReservations);
            if (exception instanceof OrderProcessingException processingException) {
                throw processingException;
            }
            throw new OrderProcessingException("Не удалось завершить создание заказа", exception);
        }
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

    private Map<Long, Integer> aggregateQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        return quantities;
    }

    private Map<Long, ProductResponse> loadProducts(Iterable<Long> productIds) {
        Map<Long, ProductResponse> products = new LinkedHashMap<>();
        for (Long productId : productIds) {
            ProductResponse product = getProduct(productId);
            if (!Boolean.TRUE.equals(product.active())) {
                throw new OrderProcessingException("Товар " + productId + " снят с продажи");
            }
            products.put(productId, product);
        }
        return products;
    }

    private ProductResponse getProduct(Long productId) {
        try {
            return productClient.getProduct(productId);
        } catch (FeignException.NotFound exception) {
            throw new OrderProcessingException("Товар " + productId + " не найден", exception);
        } catch (FeignException exception) {
            throw new OrderProcessingException("Не удалось получить данные товара " + productId, exception);
        }
    }

    private void reserve(InventoryOperationRequest request) {
        try {
            inventoryClient.reserve(request);
        } catch (FeignException.NotFound exception) {
            throw new OrderProcessingException(
                    "Складская запись для товара " + request.productId() + " не найдена", exception
            );
        } catch (FeignException.Conflict exception) {
            throw new OrderProcessingException(
                    "Недостаточно товара " + request.productId() + " для оформления заказа", exception
            );
        } catch (FeignException exception) {
            throw new OrderProcessingException(
                    "Не удалось зарезервировать товар " + request.productId(), exception
            );
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

    private OrderItemRequest toSnapshot(OrderItemRequest item, ProductResponse product) {
        return new OrderItemRequest(item.productId(), product.name(), item.quantity(), product.price());
    }

    private boolean containsLegacySnapshot(OrderItemRequest item) {
        return item.productName() != null && item.price() != null;
    }
}
