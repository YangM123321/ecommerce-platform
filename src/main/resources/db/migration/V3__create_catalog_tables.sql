-- Session 3: product module tables. All live in the "catalog" schema owned by the product module.

CREATE TABLE catalog.categories (
    id   BIGSERIAL    PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE catalog.products (
    id          BIGSERIAL     PRIMARY KEY,
    sku         VARCHAR(50)   NOT NULL UNIQUE,      -- stock keeping unit, the business identifier
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    price       NUMERIC(12,2) NOT NULL CHECK (price > 0),  -- money: NUMERIC, never floating point
    category_id BIGINT        NOT NULL REFERENCES catalog.categories (id),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,      -- soft delete: old orders still point here
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category_id ON catalog.products (category_id);

-- One row per product. "available" can be sold; "reserved" is held by placed orders.
-- The CHECK constraints are a last line of defense: stock can never go negative.
CREATE TABLE catalog.inventory (
    product_id BIGINT  PRIMARY KEY REFERENCES catalog.products (id) ON DELETE CASCADE,
    available  INTEGER NOT NULL CHECK (available >= 0),
    reserved   INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0)
);

-- Demo data so the catalog isn't empty on first start.
INSERT INTO catalog.categories (name) VALUES ('Electronics'), ('Books'), ('Home & Kitchen');

INSERT INTO catalog.products (sku, name, description, price, category_id)
SELECT s.sku, s.name, s.description, s.price, c.id
FROM (VALUES
        ('ELEC-001', 'Wireless Mouse',          'Ergonomic 2.4 GHz mouse',              24.99, 'Electronics'),
        ('ELEC-002', 'Mechanical Keyboard',     'Tenkeyless, brown switches',           89.00, 'Electronics'),
        ('ELEC-003', 'USB-C Hub',               '7-in-1 hub with HDMI',                 39.50, 'Electronics'),
        ('BOOK-001', 'Designing Data Apps',     'Distributed systems fundamentals',     45.00, 'Books'),
        ('BOOK-002', 'Clean Code Handbook',     'Writing readable software',            32.99, 'Books'),
        ('HOME-001', 'French Press',            '1 litre, borosilicate glass',          27.50, 'Home & Kitchen'),
        ('HOME-002', 'Chef Knife',              '20 cm stainless steel',                59.95, 'Home & Kitchen'),
        ('HOME-003', 'Cast Iron Pan',           '26 cm, pre-seasoned',                  42.00, 'Home & Kitchen')
     ) AS s (sku, name, description, price, category)
JOIN catalog.categories c ON c.name = s.category;

INSERT INTO catalog.inventory (product_id, available)
SELECT p.id, s.stock
FROM catalog.products p
JOIN (VALUES
        ('ELEC-001', 50), ('ELEC-002', 20), ('ELEC-003', 35),
        ('BOOK-001', 15), ('BOOK-002', 40),
        ('HOME-001', 25), ('HOME-002', 10), ('HOME-003', 12)
     ) AS s (sku, stock) ON s.sku = p.sku;
