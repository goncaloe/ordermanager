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



}