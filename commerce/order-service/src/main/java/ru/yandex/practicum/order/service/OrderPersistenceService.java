package ru.yandex.practicum.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.entity.OrderStatus;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderDto save(CreateOrderRequest request, boolean confirmed) {
        return save(request, confirmed ? OrderStatus.CONFIRMED : OrderStatus.CREATED, null);
    }

    @Transactional
    public OrderDto save(CreateOrderRequest request, OrderStatus status, String statusDetails) {
        BigDecimal totalPrice = request.items().stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(request.customerName(), request.customerEmail(), totalPrice);
        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.PENDING_CONFIRMATION) {
            order.markPendingConfirmation(statusDetails);
        }
        request.items().stream()
                .map(item -> new OrderItem(
                        item.productId(), item.productName(), item.quantity(), item.price()
                ))
                .forEach(order::addItem);

        return orderMapper.toDto(orderRepository.save(order));
    }
}
