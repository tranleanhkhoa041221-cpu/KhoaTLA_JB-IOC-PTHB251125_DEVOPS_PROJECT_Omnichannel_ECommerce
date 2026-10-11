-- ============================================================================
-- V1: Khoi tao schema cho Order & Inventory Service (Database Per Service Pattern)
-- Su dung Flyway (ddl-auto=validate) thay vi de Hibernate tu sinh schema (update),
-- dam bao schema PostgreSQL tuong minh, kiem soat duoc va apply duoc tu DB trong.
-- ============================================================================

CREATE TABLE inventories (
    id              BIGSERIAL       PRIMARY KEY,
    sku             VARCHAR(100)    NOT NULL,
    product_name    VARCHAR(255)    NOT NULL,
    available_qty   INTEGER         NOT NULL DEFAULT 0,
    reserved_qty    INTEGER         NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_inventories_available_qty_nonneg CHECK (available_qty >= 0),
    CONSTRAINT ck_inventories_reserved_qty_nonneg CHECK (reserved_qty >= 0)
);

CREATE UNIQUE INDEX idx_inventories_sku ON inventories (sku);

CREATE TABLE orders (
    id                  BIGSERIAL       PRIMARY KEY,
    order_code          VARCHAR(40)     NOT NULL,
    idempotency_key     VARCHAR(64)     NOT NULL,
    customer_id         BIGINT          NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    total_amount        NUMERIC(15, 2)  NOT NULL,
    shipping_address    VARCHAR(500)    NOT NULL,
    note                VARCHAR(500),
    cancel_reason       VARCHAR(500),
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_orders_order_code UNIQUE (order_code),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE UNIQUE INDEX idx_orders_idempotency_key ON orders (idempotency_key);

CREATE TABLE order_items (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL,
    sku             VARCHAR(100)    NOT NULL,
    product_name    VARCHAR(255)    NOT NULL,
    unit_price      NUMERIC(15, 2)  NOT NULL,
    quantity        INTEGER         NOT NULL,
    subtotal        NUMERIC(15, 2)  NOT NULL,

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE outbox_events (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(50)     NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    topic           VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,

    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_status ON outbox_events (status);
