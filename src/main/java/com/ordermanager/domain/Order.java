package com.ordermanager.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    @Setter
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Setter
    private User user;

    @Column(nullable = false)
    @Setter
    private long quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // cascade all para remover registos de order_movements quando se remove a order.
    // orphanRemoval para apagar as alocações que deixam de existir quando a quantidade
    // da encomenda é reduzida (ver releaseExcessAllocations).
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderMovement> allocations = new ArrayList<>();

    public Order(Item item, User user, long quantity) {
        this.item = item;
        this.user = user;
        this.quantity = quantity;
    }

    public void allocate(StockMovement movement, long quantity) {
        allocations.add(new OrderMovement(this, movement, quantity));
    }

    // Liberta as alocações que excedem a quantidade atual da encomenda, começando pelas
    // mais recentes. Devolve as unidades libertadas, que ficam disponíveis para outras
    // encomendas. Deve ser chamado depois de alterar quantity.
    public long releaseExcessAllocations() {
        long excess = getFulfilledQuantity() - quantity;
        if (excess <= 0) {
            return 0;
        }

        long released = 0;
        ListIterator<OrderMovement> iterator = allocations.listIterator(allocations.size());
        while (iterator.hasPrevious() && excess > 0) {
            OrderMovement allocation = iterator.previous();
            long take = Math.min(excess, allocation.getQuantity());
            allocation.reduceQuantity(take);
            excess -= take;
            released += take;
            // a alocação ficou a zero -> a linha deixa de existir (orphanRemoval)
            if (allocation.getQuantity() == 0) {
                iterator.remove();
            }
        }
        return released;
    }

    // Liberta todas as alocações (ex.: quando a encomenda passa a ser de outro item).
    public long releaseAllAllocations() {
        long released = getFulfilledQuantity();
        allocations.clear();
        return released;
    }

    // alguns métodos funcionam como campos virtuais, e não guardados na tabela da bd,
    // mas inferidos pela lista de allocations, que é a fonte da verdade

    // procura na lista de allocations se há uma OrderMovement associado ao StockMoment passado no parametro
    public Optional<OrderMovement> findAllocation(StockMovement movement) {
        return allocations.stream()
                .filter(a -> Objects.equals(a.getStockMovement().getId(), movement.getId()))
                .findFirst();
    }

    public long getRemainingQuantity() {
        return quantity - getFulfilledQuantity();
    }

    public long getFulfilledQuantity() {
        // soma quantity da lista de allocations
        return allocations.stream().mapToLong(OrderMovement::getQuantity).sum();
    }

    public boolean isComplete() {
        return getRemainingQuantity() <= 0;
    }

    public int getCompletionPercentage() {
        if (quantity <= 0) {
            return 100;
        }
        return (int) Math.min(100, Math.round(getFulfilledQuantity() * 100.0 / quantity));
    }
}