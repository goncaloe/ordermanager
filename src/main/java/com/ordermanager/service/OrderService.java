package com.ordermanager.service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.Item;
import com.ordermanager.domain.Order;
import com.ordermanager.domain.OrderMovement;
import com.ordermanager.domain.User;
import com.ordermanager.exception.BusinessException;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.OrderRepository;

@Service
public class OrderService {

    private static final Logger ORDER_LOG = LogManager.getLogger("app.orders");

    private final OrderRepository orderRepository;
    private final ItemService itemService;
    private final UserService userService;
    private final StockAllocator stockAllocator;

    public OrderService(OrderRepository orderRepository,
            ItemService itemService,
            UserService userService,
            StockAllocator stockAllocator) {
        this.orderRepository = orderRepository;
        this.itemService = itemService;
        this.userService = userService;
        this.stockAllocator = stockAllocator;
    }

    @Transactional
    public Order create(Long itemId, Long userId, long quantity) {
        // o lock do item serializa esta criação com as restantes operações de stock do item
        Item item = itemService.lock(itemId);
        User user = userService.get(userId);

        Order order = orderRepository.save(new Order(item, user, quantity));
        ORDER_LOG.info("Order {} created (item={}, qty={}, user={})", order.getId(), item.getName(), quantity, userId);

        List<OrderMovement> allocations = stockAllocator.allocateToOrder(order);
        if (allocations.isEmpty()) {
            // nada para alocar
            ORDER_LOG.info("Order {} awaiting stock: 0/{} unit(s) available", order.getId(), quantity);
        } else if (order.isComplete()) {
            ORDER_LOG.info("Order {} COMPLETED with {} unit(s) from {} stock movement(s)",
                    order.getId(), order.getQuantity(), order.getAllocations().size());
        } else {
            ORDER_LOG.info("Order {} partially fulfilled: {}/{} unit(s) from {} stock movement(s)",
                    order.getId(), order.getFulfilledQuantity(), quantity, allocations.size());
        }

        return order;
    }

    @Transactional(readOnly = true)
    public Order get(Long id) {
        return orderRepository.findByIdWithAllocations(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
    }

    @Transactional(readOnly = true)
    public Page<Order> list(Long itemId, Long userId, Pageable pageable) {
        return orderRepository.findAllFilteredWithAllocations(itemId, userId, pageable);
    }

    @Transactional
    public Order update(Long id, Long itemId, Long userId, Long quantity) {
        // lê sem lock apenas para saber que itens bloquear (o atual e o pedido)
        Order snapshot = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
        Long currentItemId = snapshot.getItem().getId();
        lockItems(currentItemId, itemId);

        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
        if (!Objects.equals(order.getItem().getId(), currentItemId)) {
            // outra transacção mudou o item entre a leitura e o lock, por isso não temos
            // o lock do item certo: falhar é mais seguro do que alocar sobre stock errado
            throw new BusinessException("Order %s was modified concurrently; retry the request".formatted(id));
        }

        boolean wasComplete = order.isComplete();
        User user = userService.get(userId);

        order.setUser(user);
        order.setQuantity(quantity);

        long released;
        if (currentItemId.equals(itemId)) {
            // reduzir a quantidade liberta o excesso; aumentar volta a tentar alocar
            released = order.releaseExcessAllocations();
        } else {
            // as alocações existentes são de movimentos do item antigo
            released = order.releaseAllAllocations();
            order.setItem(itemService.get(itemId));
        }
        stockAllocator.allocateToOrder(order);

        if (released > 0) {
            // aplicar as libertações na BD antes de recalcular as encomendas pendentes
            orderRepository.flush();
            reallocatePendingOrders(currentItemId);
        }

        ORDER_LOG.info("Order {} updated (item={}, qty={}, fulfilled={}/{}, released={})",
                order.getId(), itemId, quantity, order.getFulfilledQuantity(), quantity, released);
        if (!wasComplete && order.isComplete()) {
            completeOrder(order);
        }

        // devolve a encomenda com as relações carregadas: o mapeamento para DTO é feito
        // no controller, já fora da transacção
        return orderRepository.findByIdWithAllocations(id).orElseThrow(() -> new NotFoundException("Order", id));
    }

    // bloqueia os itens por id ascendente: a ordem global de locks é items -> orders, e
    // dentro dos items é sempre crescente para duas transacções não fecharem um ciclo
    private void lockItems(Long... itemIds) {
        Arrays.stream(itemIds).distinct().sorted().forEach(itemService::lock);
    }

    @Transactional
    public void delete(Long id) {
        // lê a encomenda sem lock só para saber que item bloquear; a ordem de locks é
        // sempre items -> orders, para não fechar ciclos com allocateToPendingOrders
        Order snapshot = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
        Long itemId = snapshot.getItem().getId();
        itemService.lock(itemId);

        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Order", id));

        orderRepository.delete(order);
        // o cascade das order_movements tem de estar aplicado na BD antes de recalcular
        // a disponibilidade das restantes encomendas
        orderRepository.flush();
        ORDER_LOG.info("Order {} deleted", id);

        // Ao apagar uma ordem liberta stock para processar outras ordens
        reallocatePendingOrders(itemId);
    }

    // como foi liberado stock do item, verifica ordens incompletas para alocar
    private void reallocatePendingOrders(Long itemId) {
        List<Order> completed = stockAllocator.allocateToPendingOrders(itemId);
        completed.forEach(this::completeOrder);
    }

    private void completeOrder(Order order) {
        ORDER_LOG.info("Order {} COMPLETED with {} unit(s) from {} stock movement(s)",
                order.getId(), order.getQuantity(), order.getAllocations().size());
    }

}