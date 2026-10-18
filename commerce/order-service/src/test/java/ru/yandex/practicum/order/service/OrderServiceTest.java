package ru.yandex.practicum.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.order.client.InventoryClient;
import ru.yandex.practicum.order.client.ProductClient;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.InventoryOperationRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.dto.ProductResponse;
import ru.yandex.practicum.order.entity.OrderStatus;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductClient productClient;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private OrderPersistenceService orderPersistenceService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldLoadRepeatedProductOnceAndReserveAggregatedQuantity() {
        ProductResponse product = new ProductResponse(1L, "Умная лампа", new BigDecimal("1490.00"), true);
        when(productClient.getProduct(1L)).thenReturn(product);
        OrderDto saved = new OrderDto(1L, "Иван", "ivan@example.com", "CONFIRMED",
                new BigDecimal("4470.00"), null, LocalDateTime.now(), List.of());
        when(orderPersistenceService.save(
                any(), org.mockito.ArgumentMatchers.eq(OrderStatus.CONFIRMED), isNull()
        )).thenReturn(saved);

        OrderDto result = orderService.create(request(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(1L, 1)
        ));

        assertThat(result.status()).isEqualTo("CONFIRMED");
        verify(productClient, times(1)).getProduct(1L);
        verify(inventoryClient).reserve(new InventoryOperationRequest(1L, 3));

        ArgumentCaptor<CreateOrderRequest> snapshot = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderPersistenceService).save(
                snapshot.capture(), org.mockito.ArgumentMatchers.eq(OrderStatus.CONFIRMED), isNull()
        );
        assertThat(snapshot.getValue().items())
                .allSatisfy(item -> {
                    assertThat(item.productName()).isEqualTo("Умная лампа");
                    assertThat(item.price()).isEqualByComparingTo("1490.00");
                });
    }

    @Test
    void shouldRejectInactiveProductWithoutReservation() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Старая лампа", BigDecimal.TEN, false));

        assertThatThrownBy(() -> orderService.create(request(new OrderItemRequest(1L, 1))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("снят с продажи");

        verify(inventoryClient, never()).reserve(any());
        verify(orderPersistenceService, never()).save(
                any(), org.mockito.ArgumentMatchers.any(OrderStatus.class), any()
        );
    }

    @Test
    void shouldReleaseAllReservationsWhenSavingOrderFails() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100"), true));
        when(productClient.getProduct(2L))
                .thenReturn(new ProductResponse(2L, "Датчик", new BigDecimal("200"), true));
        when(orderPersistenceService.save(
                any(), org.mockito.ArgumentMatchers.eq(OrderStatus.CONFIRMED), isNull()
        ))
                .thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> orderService.create(request(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)
        )))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessage("Не удалось завершить создание заказа");

        verify(inventoryClient).release(new InventoryOperationRequest(2L, 3));
        verify(inventoryClient).release(new InventoryOperationRequest(1L, 2));
    }

    @Test
    void shouldSavePendingOrderWhenProductServiceIsUnavailable() {
        when(productClient.getProduct(1L))
                .thenThrow(new ProductServiceUnavailableException(1L, new RuntimeException("timeout")));
        OrderDto pending = pendingOrder();
        when(orderPersistenceService.save(
                any(), org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING_CONFIRMATION), any()
        )).thenReturn(pending);

        OrderDto result = orderService.create(request(new OrderItemRequest(1L, 2)));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        verify(inventoryClient, never()).reserve(any());
        ArgumentCaptor<CreateOrderRequest> snapshot = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderPersistenceService).save(
                snapshot.capture(),
                org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING_CONFIRMATION),
                org.mockito.ArgumentMatchers.contains("каталог")
        );
        assertThat(snapshot.getValue().items().getFirst().productName())
                .isEqualTo("Товар #1 (ожидает проверки)");
        assertThat(snapshot.getValue().items().getFirst().price()).isZero();
    }

    @Test
    void shouldSavePendingOrderWhenInventoryServiceIsUnavailable() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100"), true));
        when(inventoryClient.reserve(new InventoryOperationRequest(1L, 2)))
                .thenThrow(new InventoryServiceUnavailableException(1L, new RuntimeException("open")));
        when(orderPersistenceService.save(
                any(), org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING_CONFIRMATION), any()
        )).thenReturn(pendingOrder());

        OrderDto result = orderService.create(request(new OrderItemRequest(1L, 2)));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        verify(orderPersistenceService).save(
                any(),
                org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING_CONFIRMATION),
                org.mockito.ArgumentMatchers.contains("складе")
        );
    }

    @Test
    void shouldRejectOrderWhenInventoryReturnsBusinessFailure() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100"), true));
        when(inventoryClient.reserve(new InventoryOperationRequest(1L, 100)))
                .thenThrow(new OrderProcessingException("Недостаточно товара для оформления заказа"));

        assertThatThrownBy(() -> orderService.create(request(new OrderItemRequest(1L, 100))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("Недостаточно товара");

        verify(orderPersistenceService, never()).save(
                any(), org.mockito.ArgumentMatchers.any(OrderStatus.class), any()
        );
    }

    private OrderDto pendingOrder() {
        return new OrderDto(10L, "Иван", "ivan@example.com", "PENDING_CONFIRMATION",
                BigDecimal.ZERO, "Требуется ручная проверка", LocalDateTime.now(), List.of());
    }

    private CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest("Иван", "ivan@example.com", List.of(items));
    }
}
