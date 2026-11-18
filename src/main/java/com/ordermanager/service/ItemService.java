package com.ordermanager.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.Item;
import com.ordermanager.exception.BusinessException;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.ItemRepository;
import com.ordermanager.repository.OrderRepository;
import com.ordermanager.repository.StockMovementRepository;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final StockMovementRepository stockMovementRepository;

    public ItemService(ItemRepository itemRepository,
                       OrderRepository orderRepository,
                       StockMovementRepository stockMovementRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public Item create(String name) {
        String trimmed = name.trim();
        if (itemRepository.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessException("An item named '%s' already exists".formatted(trimmed));
        }
        Item item = itemRepository.save(new Item(trimmed));
        return item;
    }

    @Transactional(readOnly = true)
    public Item get(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item", id));
    }

    @Transactional(readOnly = true)
    public Page<Item> list(Pageable pageable) {
        return itemRepository.findAll(pageable);
    }

    @Transactional
    public Item update(Long id, String name) {
        Item item = get(id);
        String trimmed = name.trim();
        if (itemRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new BusinessException("An item named '%s' already exists".formatted(trimmed));
        }
        item.setName(trimmed);
        return item;
    }

    @Transactional
    public void delete(Long id) {
        Item item = get(id);
        if (stockMovementRepository.existsByItemId(id) || orderRepository.existsByItemId(id)) {
            throw new BusinessException("Item '%s' cannot be deleted because it has stock movements or orders".formatted(item.getName()));
        }
        itemRepository.delete(item);
    }
}