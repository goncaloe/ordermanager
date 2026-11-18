package com.ordermanager.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordermanager.domain.Order;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {


    // obtem a order já com a informação do item e user (relações sempre presentes)
    // bloqueia uma order especifica com pessimistic lock para não haver alocações duplicadas
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o JOIN FETCH o.item JOIN FETCH o.user WHERE o.id = :id")
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
            ORDER BY o.createdAt ASC
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
}