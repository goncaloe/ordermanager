package com.ordermanager.service;

import com.ordermanager.domain.Item;
import com.ordermanager.exception.BusinessException;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
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

    public Item get(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item", id));
    }

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
        // TODO: adicionar checks quando tiver os repo de stockMovement
        itemRepository.delete(item);
    }
}