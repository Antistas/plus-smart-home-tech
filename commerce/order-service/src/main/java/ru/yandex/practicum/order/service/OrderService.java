package ru.yandex.practicum.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.client.ProductCatalogClient;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductCatalogClient productCatalogClient;

    @Transactional
    public OrderDto create(CreateOrderRequest request) {
        List<OrderItemRequest> items = request.items().stream()
                .map(this::enrichProductSnapshot)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(this::calculateItemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(request.customerName(), request.customerEmail(), totalPrice);
        items.stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.price()
                ))
                .forEach(order::addItem);

        return orderMapper.toDto(orderRepository.save(order));
    }

    public OrderDto getById(Long id) {
        return orderMapper.toDto(orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заказ с id " + id + " не найден")));
    }

    public List<OrderDto> getAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDto)
                .toList();
    }

    public List<OrderDto> getByEmail(String email) {
        return orderRepository.findAllByCustomerEmail(email).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    private BigDecimal calculateItemTotal(OrderItemRequest item) {
        return item.price().multiply(BigDecimal.valueOf(item.quantity()));
    }

    private OrderItemRequest enrichProductSnapshot(OrderItemRequest item) {
        if (item.productName() != null && item.price() != null) {
            return item;
        }

        ProductCatalogClient.ProductSnapshot product = productCatalogClient.getProduct(item.productId());
        return new OrderItemRequest(item.productId(), product.name(), item.quantity(), product.price());
    }
}
