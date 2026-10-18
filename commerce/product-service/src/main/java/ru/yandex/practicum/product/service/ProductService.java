package ru.yandex.practicum.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.product.dto.CreateProductRequest;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.dto.UpdateProductRequest;
import ru.yandex.practicum.product.entity.Product;
import ru.yandex.practicum.product.exception.NotFoundException;
import ru.yandex.practicum.product.mapper.ProductMapper;
import ru.yandex.practicum.product.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;

    public List<ProductDto> getAllActive() {
        return toDtoList(productRepository.findAllByActiveTrue());
    }

    public ProductDto getById(Long id) {
        return productMapper.toDto(findById(id));
    }

    public List<ProductDto> getByCategory(Long categoryId) {
        categoryService.findById(categoryId);
        return toDtoList(productRepository.findAllByCategoryIdAndActiveTrue(categoryId));
    }

    public List<ProductDto> search(String query) {
        return toDtoList(productRepository.findAllByNameContainingIgnoreCaseAndActiveTrue(query));
    }

    @Transactional
    public ProductDto create(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.categoryId() == null ? null : categoryService.findById(request.categoryId()));
        product.setImageUrl(request.imageUrl());
        product.setActive(true);
        return productMapper.toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, UpdateProductRequest request) {
        Product product = findById(id);

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.categoryId() != null) {
            product.setCategory(categoryService.findById(request.categoryId()));
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }

        return productMapper.toDto(productRepository.save(product));
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар с id " + id + " не найден"));
    }

    private List<ProductDto> toDtoList(List<Product> products) {
        return products.stream().map(productMapper::toDto).toList();
    }
}
