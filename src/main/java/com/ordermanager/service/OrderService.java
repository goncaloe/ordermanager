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

@Service
public class OrderService {

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
        
        List<OrderMovement> allocations = stockAllocator.allocateToOrder(order);
        if(allocations.isEmpty()){
            //nada para alocar
        }
        else {
            // TODO: fazer log dos OrderMovement alocados
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
        
        // TODO: ao apagar a order, items voltam para o stock, logo disponível para alocar a outras orders pendentes
    }
}