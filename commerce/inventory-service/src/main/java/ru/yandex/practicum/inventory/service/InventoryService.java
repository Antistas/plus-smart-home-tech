package ru.yandex.practicum.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.dto.ReserveRequest;
import ru.yandex.practicum.inventory.dto.ReserveResponse;
import ru.yandex.practicum.inventory.dto.UpdateInventoryRequest;
import ru.yandex.practicum.inventory.entity.Inventory;
import ru.yandex.practicum.inventory.exception.ConflictException;
import ru.yandex.practicum.inventory.exception.InsufficientStockException;
import ru.yandex.practicum.inventory.exception.NotFoundException;
import ru.yandex.practicum.inventory.mapper.InventoryMapper;
import ru.yandex.practicum.inventory.repository.InventoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    public List<InventoryDto> getAll() {
        return inventoryRepository.findAll().stream()
                .map(inventoryMapper::toDto)
                .toList();
    }

    public InventoryDto getByProductId(Long productId) {
        return inventoryMapper.toDto(findByProductId(productId));
    }

    @Transactional
    public InventoryDto create(UpdateInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.productId())) {
            throw new ConflictException("Складская запись для товара " + request.productId() + " уже существует");
        }

        Inventory inventory = new Inventory(request.productId(), request.quantity());
        return inventoryMapper.toDto(inventoryRepository.saveAndFlush(inventory));
    }

    @Transactional
    public InventoryDto update(UpdateInventoryRequest request) {
        Inventory inventory = findByProductId(request.productId());
        if (request.quantity() < inventory.getReservedQuantity()) {
            throw new ConflictException(
                    "Общее количество товара не может быть меньше уже зарезервированного количества"
            );
        }

        inventory.updateQuantity(request.quantity());
        return inventoryMapper.toDto(inventoryRepository.saveAndFlush(inventory));
    }

    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        Inventory inventory = findByProductId(request.productId());
        if (inventory.getAvailableQuantity() < request.quantity()) {
            throw new InsufficientStockException(
                    "Недостаточно товара " + request.productId() + ". Доступно: "
                            + inventory.getAvailableQuantity() + ", запрошено: " + request.quantity()
            );
        }

        inventory.reserve(request.quantity());
        inventoryRepository.saveAndFlush(inventory);
        return new ReserveResponse(true, inventory.getAvailableQuantity(), "Товар успешно зарезервирован");
    }

    private Inventory findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException(
                        "Складская запись для товара " + productId + " не найдена"
                ));
    }
}
