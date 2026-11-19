package com.ordermanager.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.ordermanager.dto.OrderRef;
import com.ordermanager.dto.StockMovementRequest;
import com.ordermanager.dto.StockMovementResponse;
import com.ordermanager.service.StockMovementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }


    @PostMapping
    public ResponseEntity<StockMovementResponse> create(@Valid @RequestBody StockMovementRequest request) {
        Long id = stockMovementService.create(request.itemId(), request.quantity()).getId();
        StockMovementResponse body = stockMovementService.getResponse(id);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    public Page<StockMovementResponse> list(Pageable pageable) {
        return stockMovementService.listWithAvailability(pageable);
    }

    @GetMapping("/{id}")
    public StockMovementResponse get(@PathVariable Long id) {
        return stockMovementService.getResponse(id);
    }

    // Rastreabilidade no sentido inverso: movimentos -> encomendas que os consumiram.
    // Um movimento é imutável (não há DELETE), por isso esta leitura é a forma de auditar
    // a utilização de uma entrada de stock.
    @GetMapping("/{id}/orders")
    public List<OrderRef> ordersUsingMovement(@PathVariable Long id) {
        return stockMovementService.listOrdersUsingMovement(id);
    }
}
