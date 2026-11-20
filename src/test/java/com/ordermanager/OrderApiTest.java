package com.ordermanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ordermanager.domain.Order;
import com.ordermanager.service.ItemService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.StockMovementService;
import com.ordermanager.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestContainerConfig.class)
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderService orderService;
    @Autowired
    private StockMovementService stockMovementService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;

    @Test
    @DisplayName("PUT /api/orders/{id} applies the request and recalculates the allocations")
    void putUpdatesOrderAndRecalculatesAllocations() throws Exception {
        Long itemId = itemService.create("Api " + System.nanoTime()).getId();
        Long userId = userService.create("Api " + System.nanoTime(),
                "api" + System.nanoTime() + "@example.com").getId();
        stockMovementService.create(itemId, 10);
        Order order = orderService.create(itemId, userId, 2);

        mockMvc.perform(put("/api/orders/{id}", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"itemId": %d, "userId": %d, "quantity": 6}
                        """.formatted(itemId, userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.allocations[0].quantity").value(6));
    }

    @Test
    @DisplayName("A stock movement cannot be deleted (405), it is immutable")
    void stockMovementCannotBeDeleted() throws Exception {
        Long itemId = itemService.create("Api " + System.nanoTime()).getId();
        Long movementId = stockMovementService.create(itemId, 5).getId();

        mockMvc.perform(delete("/api/stock-movements/{id}", movementId))
                .andExpect(status().isMethodNotAllowed());

        // o movimento continua a existir
        mockMvc.perform(get("/api/stock-movements/{id}", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    @DisplayName("GET /api/stock-movements/{id}/orders traces back the orders that used it")
    void movementTracesBackToOrders() throws Exception {
        Long itemId = itemService.create("Api " + System.nanoTime()).getId();
        Long userId = userService.create("Api " + System.nanoTime(),
                "api" + System.nanoTime() + "@example.com").getId();
        Long movementId = stockMovementService.create(itemId, 5).getId();
        Order order = orderService.create(itemId, userId, 5);

        mockMvc.perform(get("/api/stock-movements/{id}/orders", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(order.getId()))
                .andExpect(jsonPath("$[0].complete").value(true));
    }
}
