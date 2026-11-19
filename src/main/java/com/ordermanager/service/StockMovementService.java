package com.ordermanager.service;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.Item;
import com.ordermanager.domain.Order;
import com.ordermanager.domain.StockMovement;
import com.ordermanager.dto.OrderRef;
import com.ordermanager.dto.StockMovementResponse;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.StockMovementRepository;

@Service
public class StockMovementService {

    private static final Logger STOCK_LOG = LogManager.getLogger("app.stock");
    private static final Logger ORDER_LOG = LogManager.getLogger("app.orders");

    private final StockMovementRepository stockMovementRepository;
    private final ItemService itemService;
    private final StockAllocator stockAllocator;

    public StockMovementService(StockMovementRepository stockMovementRepository, ItemService itemService,
            StockAllocator stockAllocator) {
        this.stockMovementRepository = stockMovementRepository;
        this.itemService = itemService;
        this.stockAllocator = stockAllocator;
    }

    // Regista stock de um item; se houver pedidos antigos do item à espera de stock
    // será atribuido a movimentos dessa encomenda
    @Transactional
    public StockMovement create(Long itemId, long quantity) {
        Item item = itemService.get(itemId);

        StockMovement movement = stockMovementRepository.save(new StockMovement(item, quantity));

        STOCK_LOG.info("Stock movement {} created (item={}, qty={})", movement.getId(), item.getName(), quantity);
        // aloca movimentos se for possível
        List<Order> completed = stockAllocator.allocateToPendingOrders(item.getId());
        completed.forEach(order -> {
            ORDER_LOG.info("Order {} COMPLETED by stock movement {} ({}/{} unit(s))", order.getId(), movement.getId(),
                    order.getFulfilledQuantity(), order.getQuantity());
            // TODO: seria aqui que enviamos email?
        });

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
        return StockMovementResponse.from(movement, 
            stockMovementRepository.allocatedQuantityForMovement(id));
    }

    public Page<StockMovementResponse> listWithAvailability(Pageable pageable) {
        Page<StockMovement> page = stockMovementRepository.findAll(pageable);
        return page.map(movement -> StockMovementResponse.from(movement,
            stockMovementRepository.allocatedQuantityForMovement(movement.getId())));
    }

    // Encomendas que consumiram este movimento, das mais antigas para as mais recentes.
    // Substitui o antigo DELETE: o histórico não é apagado, é consultado.
    @Transactional(readOnly = true)
    public List<OrderRef> listOrdersUsingMovement(Long id) {
        get(id); // 404 se o movimento não existir
        return stockMovementRepository.findOrderRefsUsingMovement(id).stream()
                .map(OrderRef::from)
                .toList();
    }

    // obtem a quantidade de stock livre de um item: total de entradas positivas -
    // total items processados
    public long availableStockOf(Long itemId) {
        itemService.get(itemId); // lança 404 se o item não existir
        return availableStock(itemId);
    }

    public long availableStock(Long itemId) {
        return Math.max(0, stockMovementRepository.calculateAvailableStock(itemId));
    }
}