package com.ordermanager.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.Item;
import com.ordermanager.domain.Order;
import com.ordermanager.domain.OrderMovement;
import com.ordermanager.domain.User;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.OrderRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        Item item = itemService.get(itemId);
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
            // TODO: fazer log dos OrderMovement alocados
            ORDER_LOG.info("Order {} partially fulfilled: {}/{} unit(s)",
                    order.getId(), order.getFulfilledQuantity(), quantity);
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
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Order", id));

        // TODO: recalcular a alocação de movimentos

        return order;
    }

    @Transactional
    public void delete(Long id) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Order", id));

        orderRepository.delete(order);
        ORDER_LOG.info("Order {} deleted", id);
        
        // TODO: ao apagar a order, items voltam para o stock, logo disponível para
        // alocar a outras orders pendentes
    }
}