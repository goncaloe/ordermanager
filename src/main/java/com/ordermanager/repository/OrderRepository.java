package com.ordermanager.repository;

import java.util.Collection;
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
            SELECT DISTINCT o 
            FROM Order o
              JOIN FETCH o.item
              JOIN FETCH o.user
              LEFT JOIN FETCH o.allocations a
              LEFT JOIN FETCH a.stockMovement
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithAllocations(@Param("id") Long id);


    // Paginação em duas fases, 1ª fase: só os ids da página pedida, sem joins de
    // colecções. Combinar Pageable com JOIN FETCH de uma colecção faz o Hibernate paginar
    // em memória (carrega tudo) e o LIMIT passa a contar linhas do join, não encomendas.
    // parametros :itemId e :userId opcionais: se != null aplica na query
    @Query(value = """
            SELECT o.id
            FROM Order o
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
    Page<Long> findFilteredIds(@Param("itemId") Long itemId,
                               @Param("userId") Long userId,
                               Pageable pageable);

    // Paginação em duas fases, 2ª fase: as entidades da página, já com todas as relações
    // (JOIN FETCH de colecção é seguro aqui porque o conjunto de ids já vem limitado)
    @Query("""
            SELECT DISTINCT o
            FROM Order o
              JOIN FETCH o.item
              JOIN FETCH o.user
              LEFT JOIN FETCH o.allocations a
              LEFT JOIN FETCH a.stockMovement
            WHERE o.id IN :ids
            """)
    List<Order> findAllWithAllocationsByIds(@Param("ids") Collection<Long> ids);


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