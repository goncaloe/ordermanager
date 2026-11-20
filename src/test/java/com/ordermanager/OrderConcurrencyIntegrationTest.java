package com.ordermanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ordermanager.domain.Order;
import com.ordermanager.service.ItemService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.StockMovementService;
import com.ordermanager.service.UserService;

// Sem @Transactional: cada thread tem de correr na sua própria transacção, senão não há
// concorrência real. Por isso cada teste usa um item e um utilizador próprios, para não
// depender de limpeza de dados entre testes.
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfig.class)
class OrderConcurrencyIntegrationTest {

    private static final int TIMEOUT_SECONDS = 60;

    @Autowired
    private OrderService orderService;
    @Autowired
    private StockMovementService stockMovementService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long itemId;
    private Long userId;

    @BeforeEach
    void setUp() {
        itemId = itemService.create("Widget " + System.nanoTime()).getId();
        userId = userService.create("Goncalo " + System.nanoTime(), "goncalo" + System.nanoTime() + "@example.com")
                .getId();
    }

    @Test
    @DisplayName("Concurrent orders never allocate the same stock units twice")
    void concurrentOrdersNeverOverAllocate() throws Exception {
        stockMovementService.create(itemId, 10);
        int attempts = 20;

        List<Order> orders = runConcurrently(
                Collections.nCopies(attempts, (Callable<Order>) () -> orderService.create(itemId, userId, 1)));

        long fulfilled = orders.stream().mapToLong(Order::getFulfilledQuantity).sum();
        long completed = orders.stream().filter(Order::isComplete).count();

        // 10 unidades para 20 encomendas de 1: exatamente 10 completas, sem alocações duplicadas
        assertThat(fulfilled).isEqualTo(10);
        assertThat(completed).isEqualTo(10);
        assertThat(stockMovementService.availableStock(itemId)).isZero();
        assertThat(overAllocatedGroups(itemId)).isZero();
    }

    @Test
    @DisplayName("Concurrent stock arrivals never allocate the same units twice")
    void concurrentStockArrivalsNeverOverAllocate() throws Exception {
        int pendingOrders = 10;
        for (int i = 0; i < pendingOrders; i++) {
            orderService.create(itemId, userId, 2); // 20 unidades pedidas, sem stock
        }

        runConcurrently(Collections.nCopies(10,
                (Callable<Object>) () -> stockMovementService.create(itemId, 1))); // 10 unidades

        long allocated = totalAllocated(itemId);

        assertThat(allocated).isEqualTo(10);
        assertThat(stockMovementService.availableStock(itemId)).isZero();
        assertThat(overAllocatedGroups(itemId)).isZero();
        // FIFO: as primeiras 5 encomendas ficam completas (2 + 2 + 2 + 2 + 2), as restantes a zero
        CompleteCounts counts = fulfilledPerOrder();
        assertThat(counts.completed()).isEqualTo(5);
        assertThat(counts.pending()).isEqualTo(5);
    }

    @Test
    @DisplayName("Creating orders and receiving stock at the same time keeps the stock coherent")
    void concurrentOrdersAndStockArrivalsReachEquilibrium() throws Exception {
        int units = 10;
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < units; i++) {
            tasks.add(() -> orderService.create(itemId, userId, 1));
            tasks.add(() -> stockMovementService.create(itemId, 1));
        }

        runConcurrently(tasks);

        // 10 unidades para 10 encomendas de 1: todas completas, nada perdido nem duplicado
        CompleteCounts counts = fulfilledPerOrder();
        assertThat(counts.total()).isEqualTo(units);
        assertThat(counts.completed()).isEqualTo(units);
        assertThat(totalAllocated(itemId)).isEqualTo(units);
        assertThat(stockMovementService.availableStock(itemId)).isZero();
        assertThat(overAllocatedGroups(itemId)).isZero();
    }

    private record CompleteCounts(int total, int completed, int pending) {
    }

    private CompleteCounts fulfilledPerOrder() {
        List<Order> orders = orderService.list(itemId, null, PageRequest.of(0, 100)).getContent();
        long completed = orders.stream().filter(Order::isComplete).count();
        return new CompleteCounts(orders.size(), (int) completed, (int) (orders.size() - completed));
    }

    private long totalAllocated(Long itemId) {
        Long total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.quantity), 0)
                FROM order_movements a
                JOIN orders o ON o.id = a.order_id
                WHERE o.item_id = ?
                """, Long.class, itemId);
        return total == null ? 0 : total;
    }

    // Invariantes verificados na BD, independentes do estado em memória: nenhum movimento
    // com mais unidades alocadas do que a sua quantidade e nenhuma encomenda acima do pedido.
    private long overAllocatedGroups(Long itemId) {
        Long movements = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT m.id
                    FROM stock_movements m
                    JOIN order_movements a ON a.stock_movement_id = m.id
                    WHERE m.item_id = ?
                    GROUP BY m.id, m.quantity
                    HAVING SUM(a.quantity) > m.quantity OR MIN(a.quantity) <= 0
                ) broken
                """, Long.class, itemId);
        Long orders = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT o.id
                    FROM orders o
                    JOIN order_movements a ON a.order_id = o.id
                    WHERE o.item_id = ?
                    GROUP BY o.id, o.quantity
                    HAVING SUM(a.quantity) > o.quantity
                ) broken
                """, Long.class, itemId);
        return (movements == null ? 0 : movements) + (orders == null ? 0 : orders);
    }

    private <T> List<T> runConcurrently(List<? extends Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                // future.get propaga as excepções das threads: uma falha falha o teste
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
