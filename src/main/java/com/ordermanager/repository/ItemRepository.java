package com.ordermanager.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.ordermanager.domain.Item;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ItemRepository extends JpaRepository<Item, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Bloqueia a linha do item até ao fim da transacção. É o ponto de serialização de
    // todas as operações que mexem no stock do item: criar/alterar/apagar encomendas e
    // registar movimentos. Sem este lock, duas transacções podem calcular a mesma
    // disponibilidade e alocar as mesmas unidades a encomendas diferentes.
    // O timeout evita que um pedido fique indefinidamente à espera do lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);
}
