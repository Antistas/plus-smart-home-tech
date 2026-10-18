package ru.yandex.practicum.order.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Boolean active
) {
}
