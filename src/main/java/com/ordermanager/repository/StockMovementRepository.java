package com.ordermanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ordermanager.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // obtem a quantidade de stock livre de um item. Soma de: total de entradas positivas - total items processados
    // só conta os StockMovement com quantidade positiva
    // usar COALESCE ter 0 quando não há registos
    @Query("""
            SELECT (SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m WHERE m.item.id = :itemId AND m.quantity > 0)
                 - (SELECT COALESCE(SUM(a.quantity), 0) FROM OrderMovement a WHERE a.stockMovement.item.id = :itemId)
            """)
    long calculateAvailableStock(@Param("itemId") Long itemId);

}
