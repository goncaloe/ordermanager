package com.ordermanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordermanager.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsByItemId(Long itemId);

    // obtem a quantidade de stock livre de um item. Soma de: total de entradas positivas - total items processados
    // só conta os StockMovement com quantidade positiva
    // usar COALESCE ter 0 quando não há registos
    @Query("""
            SELECT (SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m WHERE m.item.id = :itemId AND m.quantity > 0)
                 - (SELECT COALESCE(SUM(a.quantity), 0) FROM OrderMovement a WHERE a.stockMovement.item.id = :itemId)
            """)
    long calculateAvailableStock(@Param("itemId") Long itemId);


    // Lista de movimentos positivos do item, do mais antigo para o mais recente, cada um com quanto já foi processado
    // Elementos não precisam fazer populate em objetos, basta um par guardado em Objet com 2 elementos:
    // [StockMovement, allocated]. Lista vem na ordem created_at para garantir LIFO no fulfuill.
    @Query("""
            SELECT m, COALESCE((SELECT SUM(a.quantity) FROM OrderMovement a WHERE a.stockMovement = m), 0)
            FROM StockMovement m
            WHERE m.item.id = :itemId
              AND m.quantity > 0
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<Object[]> findPositiveMovementsWithAllocatedQuantity(@Param("itemId") Long itemId);


    @Query("""
            SELECT o.id AS id,
                   o.user.id AS userId,
                   o.quantity AS quantity,
                   o.createdAt AS createdAt,
                   COALESCE((SELECT SUM(a2.quantity) FROM OrderMovement a2 WHERE a2.order = o), 0) AS fulfilledQuantity
            FROM OrderMovement a
            JOIN a.order o
            WHERE a.stockMovement.id = :movementId
            ORDER BY o.createdAt ASC, o.id ASC
            """)
    List<OrderRefProjection> findOrderRefsUsingMovement(@Param("movementId") Long movementId);

    @Modifying
    @Query("DELETE FROM OrderMovement a WHERE a.stockMovement.id = :movementId")
    void deleteAllocationsUsingMovement(@Param("movementId") Long movementId);

}
