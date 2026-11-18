package com.ordermanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordermanager.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {


}
