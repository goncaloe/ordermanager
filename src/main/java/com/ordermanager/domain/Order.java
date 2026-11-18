package com.ordermanager.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private User user;

    @Column(nullable = false)
    @Setter
    private long quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // cascade all para remover registos de order_movements quando se remove a order
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderMovement> allocations = new ArrayList<>();

    public Order(Item item, User user, long quantity) {
        this.item = item;
        this.user = user;
        this.quantity = quantity;
    }

    public void allocate(StockMovement movement, long quantity) {
        allocations.add(new OrderMovement(this, movement, quantity));
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

}