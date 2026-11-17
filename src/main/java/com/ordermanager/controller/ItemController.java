package com.ordermanager.controller;

import com.ordermanager.domain.Item;
import com.ordermanager.dto.ItemRequest;
import com.ordermanager.dto.ItemResponse;
import com.ordermanager.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public Page<ItemResponse> list(Pageable pageable) {
        return itemService.list(pageable).map(ItemResponse::from);
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable Long id) {
        return ItemResponse.from(itemService.get(id));
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest request) {
        Item item = itemService.create(request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(item.getId()).toUri();
        return ResponseEntity.created(location).body(ItemResponse.from(item));
    }

    @PutMapping("/{id}")
    public ItemResponse update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return ItemResponse.from(itemService.update(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}