package com.ordermanager;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.Order;
import com.ordermanager.domain.StockMovement;
import com.ordermanager.service.ItemService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.StockMovementService;
import com.ordermanager.service.UserService;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfig.class)
@Transactional
class OrderFulfilmentIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private StockMovementService stockMovementService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;

    private Long itemId;
    private Long userId;

    @BeforeEach
    void setUp() {
        itemId = itemService.create("Widget " + System.nanoTime()).getId();
        userId = userService.create("Goncalo " + System.nanoTime(), "goncalo" + System.nanoTime() + "@example.com").getId();
    }

    @Test
    @DisplayName("An order created with enough stock is complete immediately")
    void orderIsSatisfiedOnCreation() {
        stockMovementService.create(itemId, 10);

        Order order = orderService.create(itemId, userId, 4);

        assertThat(order.isComplete()).isTrue();
        assertThat(order.getFulfilledQuantity()).isEqualTo(4);
        assertThat(order.getCompletionPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("An order without stock stays pending and is completed when stock arrives")
    void orderCompletesWhenStockArrives() {
        //encomenda pendente concluída quando o stock chega (parcial -> total);
        Order order = orderService.create(itemId, userId, 10);
        assertThat(order.isComplete()).isFalse();
        assertThat(order.getCompletionPercentage()).isZero();

        stockMovementService.create(itemId, 4);
        Order afterPartial = orderService.get(order.getId());
        assertThat(afterPartial.getFulfilledQuantity()).isEqualTo(4);
        assertThat(afterPartial.getCompletionPercentage()).isEqualTo(40);
        assertThat(afterPartial.isComplete()).isFalse();

        stockMovementService.create(itemId, 6);
        Order afterComplete = orderService.get(order.getId()); // neste ponto a ordem fica completamente alocada
        assertThat(afterComplete.isComplete()).isTrue();
        assertThat(afterComplete.getFulfilledQuantity()).isEqualTo(10);

    }

    @Test
    @DisplayName("Stock is attributed to the oldest pending order first (FIFO)")
    void stockGoesToOldestOrderFirst() {
        // este teste verifica se a atribuição segue FIFO (a encomenda mais antiga é alocada primeiro);
        Order first = orderService.create(itemId, userId, 5);
        Order second = orderService.create(itemId, userId, 5);

        stockMovementService.create(itemId, 6);

        Order firstAfter = orderService.get(first.getId());
        Order secondAfter = orderService.get(second.getId());

        assertThat(firstAfter.isComplete()).isTrue();
        assertThat(secondAfter.getFulfilledQuantity()).isEqualTo(1);
        assertThat(secondAfter.isComplete()).isFalse();
    }

    
    @Test
    @DisplayName("An order spanning several movements traces all of them, and each movement traces back")
    void traceIsBidirectional() {
        Order order = orderService.create(itemId, userId, 15);

        StockMovement first = stockMovementService.create(itemId, 10);
        StockMovement second = stockMovementService.create(itemId, 10);

        Order completed = orderService.get(order.getId());
        assertThat(completed.isComplete()).isTrue();
        assertThat(completed.getAllocations()).hasSize(2);

        // order -> movements (verifica as alocações associadas)
        assertThat(completed.getAllocations())
                .extracting(a -> a.getStockMovement().getId())
                .containsExactly(first.getId(), second.getId());

        //TODO: verificar que first e second tem order associado
    }
    
    @Test
    @DisplayName("A movement is never suprasses beyond its own quantity")
    void movementIsNeverOverAllocated() {
        stockMovementService.create(itemId, 5);
        Order first = orderService.create(itemId, userId, 5);
        Order second = orderService.create(itemId, userId, 5);

        // o fulfill quantity não deve ultrapassar a quantidade da order
        assertThat(orderService.get(first.getId()).getFulfilledQuantity()).isEqualTo(5);
        assertThat(orderService.get(second.getId()).getFulfilledQuantity()).isZero();

        // O movement da primeira order está totalmente alocado.
        Long usedMovementId = orderService.get(first.getId()).getAllocations().getFirst()
                .getStockMovement().getId();
        var response = stockMovementService.getResponse(usedMovementId);
        assertThat(response.allocatedQuantity()).isEqualTo(5);
        assertThat(response.availableQuantity()).isZero();
        assertThat(stockMovementService.availableStockOf(itemId)).isZero();
    }

    @Test
    @DisplayName("Deleting an order frees its stock for a pending order")
    void deletingOrderFreesStock() {
        stockMovementService.create(itemId, 5);
        Order first = orderService.create(itemId, userId, 5);
        Order second = orderService.create(itemId, userId, 5);
        assertThat(orderService.get(second.getId()).getFulfilledQuantity()).isZero();

        orderService.delete(first.getId());

        Order secondAfter = orderService.get(second.getId());
        assertThat(secondAfter.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Negative movements never satisfy orders")
    void negativeMovementDoesNotFulfil() {
        stockMovementService.create(itemId, -5);
        Order order = orderService.create(itemId, userId, 3);

        assertThat(order.getFulfilledQuantity()).isZero();
        assertThat(stockMovementService.availableStock(itemId)).isZero();
    }

    @Test
    @DisplayName("Only positive movement quantity counts towards availability")
    void availabilityIgnoresNegativeMovements() {
        stockMovementService.create(itemId, 8);
        stockMovementService.create(itemId, -3);

        assertThat(stockMovementService.availableStock(itemId)).isEqualTo(8);
    }
}