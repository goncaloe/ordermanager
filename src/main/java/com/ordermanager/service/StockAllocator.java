package com.ordermanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ordermanager.domain.Order;
import com.ordermanager.domain.OrderMovement;
import com.ordermanager.domain.StockMovement;
import com.ordermanager.repository.StockMovementRepository;


// Não é um service de api, por isso pode ser @Component
@Component
public class StockAllocator {

    private final StockMovementRepository stockMovementRepository;

    public StockAllocator(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    // dada uma order, procura por registos de StockMovement disponíveis até completar a quantidade restante da order.
    // revolve a lista de OrderMovement para guardar na bd
    public List<OrderMovement> allocateToOrder(Order order) {
        long missing = order.getRemainingQuantity();
        if (missing <= 0) {
            return List.of();
        }

        List<Object[]> rows = stockMovementRepository.findPositiveMovementsWithAllocatedQuantity(order.getItem().getId());
        List<OrderMovement> created = new ArrayList<>();

        for (Object[] row : rows) {
            if (missing <= 0) {
                break;
            }
            StockMovement movement = (StockMovement) row[0];
            long allocated = ((Number) row[1]).longValue();
            long free = movement.getQuantity() - allocated;
            if (free <= 0) {
                continue;
            }

            // take ≤ free, logo a soma das alocações de um movimento nunca passa a movement.quantity
            long take = Math.min(free, missing);

            // se já existir um OrderMoment parcialmente preenchido, tenta alocar o restante de quantity.
            Optional<OrderMovement> existing = order.findAllocation(movement);
            if (existing.isPresent()) {
                OrderMovement allocation = existing.get();
                allocation.addQuantity(take);
                created.add(allocation);
            } else {
                OrderMovement allocation = new OrderMovement(order, movement, take);
                order.getAllocations().add(allocation); //adiciona registo na tabela order_movements;
                created.add(allocation);
            }

            missing -= take;
        }

        return created;
    }

}