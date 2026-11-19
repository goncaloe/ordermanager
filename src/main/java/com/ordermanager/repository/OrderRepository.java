package com.ordermanager.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.ordermanager.domain.Order;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByItemId(Long itemId);

    // Bloqueia uma encomenda específica, até ao fim da transacção. O SELECT é simples
    // (sem JOIN FETCH) para o FOR UPDATE bloquear apenas a linha de orders: as relações
    // são carregadas lazy dentro da transacção. O timeout evita esperas indefinidas.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // obtem a order já com toda a informação numa query. Versão sem filtros
    @Query("""
            SELECT o 
            FROM Order o
              JOIN FETCH o.item
              JOIN FETCH o.user
              LEFT JOIN FETCH o.allocations a
              LEFT JOIN FETCH a.stockMovement
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithAllocations(@Param("id") Long id);


    // queries de orders para listagens paginadas, incluir todas as relações
    // LEFT JOIN onde as relações podem ter 0 elementos
    // parametros :itemId e :userId opcionais: se != null aplica na query
    @Query(value = """
            SELECT o 
            FROM Order o
              JOIN FETCH o.item
              JOIN FETCH o.user
              LEFT JOIN FETCH o.allocations a
              LEFT JOIN FETCH a.stockMovement
            WHERE (:itemId IS NULL OR o.item.id = :itemId)
              AND (:userId IS NULL OR o.user.id = :userId)
            ORDER BY o.createdAt ASC, o.id ASC
            """,
            countQuery = """
            SELECT COUNT(o)
            FROM Order o
            WHERE (:itemId IS NULL OR o.item.id = :itemId)
              AND (:userId IS NULL OR o.user.id = :userId)
            """)
    Page<Order> findAllFilteredWithAllocations(@Param("itemId") Long itemId,
                                               @Param("userId") Long userId,
                                               Pageable pageable);


    // Ids das orders incompletas, das mais antigas para as recentes
    // ordem incompleta = quando a soma de quantity das order_movements é inferior a order.quantity
    // Devolve apenas ids: cada encomenda é depois carregada já com o lock, para não haver
    // estado antigo no persistence context
    @Query("""
            SELECT o.id
            FROM Order o
            WHERE o.item.id = :itemId
              AND o.quantity > (SELECT COALESCE(SUM(a.quantity), 0) FROM OrderMovement a WHERE a.order = o)
            ORDER BY o.createdAt ASC, o.id ASC
            """)
    List<Long> findPendingOrderIdsForItem(@Param("itemId") Long itemId);
}