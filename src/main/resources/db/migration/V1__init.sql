
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_items_name UNIQUE (name)
);

CREATE TABLE stock_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES items (id),
    quantity BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_movements_item ON stock_movements (item_id);


CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES items (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_item ON orders (item_id);
CREATE INDEX idx_orders_user ON orders (user_id);

-- Movimentos parciais para o fulfill das encomendas
CREATE TABLE order_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id),
    stock_movement_id BIGINT NOT NULL REFERENCES stock_movements (id),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_movements UNIQUE (order_id, stock_movement_id)
);
CREATE INDEX idx_order_movements_movement ON order_movements (stock_movement_id);