-- Session 3: order module tables. All live in the "ordering" schema owned by the order module.

-- user_id and product_id are plain numbers with NO foreign keys to other schemas, on purpose:
-- users and products belong to other modules, and in Session 4 they move to other databases.
CREATE TABLE ordering.orders (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_id_created_at ON ordering.orders (user_id, created_at DESC);

-- product_name and unit_price are copied at order time, so later price changes
-- don't rewrite what the customer actually paid.
CREATE TABLE ordering.order_items (
    id           BIGSERIAL     PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(200)  NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL,
    quantity     INTEGER       NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON ordering.order_items (order_id);
