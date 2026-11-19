-- Índices para as consultas de stock/alocações e para a listagem paginada de encomendas.
-- A disponibilidade e o cumprimento são derivados de order_movements (sem colunas
-- materializadas e sem FUNCTION/TRIGGER), por isso os SUM passam a poder ser respondidos
-- só com o índice (INCLUDE quantity).

-- Listagem filtrada e ordenada por created_at, id (paginação em duas fases, ver OrderService.list).
-- Os índices compostos substituem os índices simples de item_id/user_id, que são prefixos destes.
DROP INDEX IF EXISTS idx_orders_item;
DROP INDEX IF EXISTS idx_orders_user;
CREATE INDEX idx_orders_item_created_at ON orders (item_id, created_at, id);
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at, id);
CREATE INDEX idx_orders_created_at_id ON orders (created_at, id);

-- SUM(quantity) por encomenda: quantidade cumprida e encomendas pendentes.
CREATE INDEX idx_order_movements_order_qty ON order_movements (order_id) INCLUDE (quantity);

-- SUM(quantity) por movimento: stock livre de cada movimento.
DROP INDEX IF EXISTS idx_order_movements_movement;
CREATE INDEX idx_order_movements_movement_qty ON order_movements (stock_movement_id) INCLUDE (quantity);
