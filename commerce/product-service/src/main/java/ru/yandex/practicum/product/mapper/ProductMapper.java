package ru.yandex.practicum.product.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.entity.Product;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final CategoryMapper categoryMapper;

    public ProductDto toDto(Product product) {
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory() == null ? null : categoryMapper.toDto(product.getCategory()),
                product.getImageUrl(),
                product.isActive()
        );
    }
}
