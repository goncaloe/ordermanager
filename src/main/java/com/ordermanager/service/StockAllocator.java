package com.ordermanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ordermanager.domain.Order;
import com.ordermanager.domain.OrderMovement;
import com.ordermanager.domain.StockMovement;
import com.ordermanager.repository.OrderRepository;
import com.ordermanager.repository.StockMovementRepository;

import jakarta.persistence.EntityManager;


// Não é um service de api, por isso pode ser @Component.
// Nenhum destes métodos é seguro por si só: quem chama tem de ter o lock do item
// (ItemService.lock) na mão, para que as leituras de disponibilidade e as escritas de
// alocações do mesmo item fiquem serializadas.
@Component
public class StockAllocator {

    private final OrderRepository orderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EntityManager entityManager;

    public StockAllocator(OrderRepository orderRepository, StockMovementRepository stockMovementRepository,
            EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.entityManager = entityManager;
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

        if (!created.isEmpty()) {
            // a disponibilidade é derivada de order_movements, por isso as alocações desta
            // transacção têm de estar na BD antes da próxima leitura: sem isto, a encomenda
            // pendente seguinte voltaria a ver esse stock como livre
            entityManager.flush();
        }

        return created;
    }


    // quando entra stock de um item, verifica cada order incompleta para alocar
    // processa primeiro orders mais antigas (FIFO)
    public List<Order> allocateToPendingOrders(Long itemId) {
        List<Order> completed = new ArrayList<>();

        // só ids: o lock e o carregamento da entidade acontecem no findByIdForUpdate,
        // sem nenhuma leitura anterior desta order no persistence context
        for (Long orderId : orderRepository.findPendingOrderIdsForItem(itemId)) {
            Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null || order.isComplete()) {
                continue;
            }
            List<OrderMovement> created = allocateToOrder(order);
            if (created.isEmpty()) {
                // só se pode parar quando o item já não tem stock livre: assumir que
                // "não alocou a esta, não aloca às seguintes" deixaria encomendas sem tentativa
                if (stockMovementRepository.calculateAvailableStock(itemId) <= 0) {
                    break;
                }
                continue;
            }
            if (order.isComplete()) {
                completed.add(order);
            }
        }
        return completed;
    }

}
