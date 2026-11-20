package com.ordermanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.ordermanager.domain.Order;
import com.ordermanager.dto.OrderResponse;
import com.ordermanager.service.ItemService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.StockMovementService;
import com.ordermanager.service.UserService;

// Sem @Transactional, como no controller: a transacção do service fecha antes do mapeamento
// para DTO. Garante que não há LazyInitializationException na resposta HTTP.
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfig.class)
class OrderResponseMappingTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private StockMovementService stockMovementService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Order DTOs can be built outside the service transaction")
    void dtoMappingOutsideTransaction() {
        Long itemId = itemService.create("Mapping " + System.nanoTime()).getId();
        Long userId = userService.create("Mapping " + System.nanoTime(),
                "mapping" + System.nanoTime() + "@example.com").getId();
        stockMovementService.create(itemId, 10);

        Order created = orderService.create(itemId, userId, 2);
        OrderResponse createdResponse = OrderResponse.from(created);
        assertThat(createdResponse.allocations()).hasSize(1);
        assertThat(createdResponse.allocations().getFirst().stockMovementCreatedAt()).isNotNull();

        Order updated = orderService.update(created.getId(), itemId, userId, 6L);
        OrderResponse updatedResponse = OrderResponse.from(updated);
        assertThat(updatedResponse.quantity()).isEqualTo(6);
        assertThat(updatedResponse.allocations()).isNotEmpty();
        assertThat(updatedResponse.allocations().getFirst().stockMovementCreatedAt()).isNotNull();

        Page<OrderResponse> page = orderService.list(itemId, null, PageRequest.of(0, 10))
                .map(OrderResponse::from);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().allocations()).isNotEmpty();
    }
}
