package com.ordermanager.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.Item;
import com.ordermanager.domain.StockMovement;
import com.ordermanager.dto.StockMovementResponse;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.StockMovementRepository;

@Service
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ItemService itemService;

    public StockMovementService(StockMovementRepository stockMovementRepository, ItemService itemService) {
        this.stockMovementRepository = stockMovementRepository;
        this.itemService = itemService;
    }

    // Regista stock de um item; se houver pedidos antigos do item à espera de stock será atribuido a movimentos dessa encomenda
    @Transactional
    public StockMovement create(Long itemId, long quantity) {
        Item item = itemService.get(itemId);
        StockMovement movement = stockMovementRepository.save(new StockMovement(item, quantity));

        return movement;
    }

    @Transactional(readOnly = true)
    public StockMovement get(Long id) {
        return stockMovementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("StockMovement", id));
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> list(Pageable pageable) {
        return stockMovementRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public StockMovementResponse getResponse(Long id) {
        StockMovement movement = get(id);
        return StockMovementResponse.from(movement);
    }

    public Page<StockMovementResponse> listWithAvailability(Pageable pageable) {
        Page<StockMovement> page = stockMovementRepository.findAll(pageable);
        return page.map(movement -> StockMovementResponse.from(movement));
    }


    @Transactional
    public void delete(Long id) {
        StockMovement movement = get(id);

        stockMovementRepository.delete(movement);
        stockMovementRepository.flush();
    }



    // obtem a quantidade de stock livre de um item: total de entradas positivas - total items processados
    public long availableStockOf(Long itemId) {
        itemService.get(itemId); // lança 404 se o item não existir
        return availableStock(itemId);
    }

    public long availableStock(Long itemId) {
        return Math.max(0, stockMovementRepository.calculateAvailableStock(itemId));
    }
}