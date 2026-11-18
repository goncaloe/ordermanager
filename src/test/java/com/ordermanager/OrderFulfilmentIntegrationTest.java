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

}