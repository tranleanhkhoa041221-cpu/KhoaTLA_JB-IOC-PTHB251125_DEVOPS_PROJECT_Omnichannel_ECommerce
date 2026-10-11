-- ============================================================================
-- V1: Khoi tao schema cho Payment & Billing Service (Database Per Service Pattern)
-- Su dung Flyway (ddl-auto=validate) thay vi de Hibernate tu sinh schema, vi day
-- la service xu ly tien - can kiem soat schema tuong minh, co the review truoc khi apply.
-- ============================================================================

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT          NOT NULL,
    order_code          VARCHAR(40)     NOT NULL,
    customer_id         BIGINT          NOT NULL,
    amount              NUMERIC(15, 2)  NOT NULL,
    currency            VARCHAR(10)     NOT NULL DEFAULT 'VND',
    payment_method      VARCHAR(20)     NOT NULL DEFAULT 'VNPAY',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    vnp_txn_ref         VARCHAR(50)     NOT NULL,
    vnp_create_date     VARCHAR(20),
    vnp_transaction_no  VARCHAR(50),
    bank_code           VARCHAR(20),
    card_type           VARCHAR(20),
    paid_at             TIMESTAMP,
    failure_reason      VARCHAR(500),
    refunded_amount     NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_vnp_txn_ref UNIQUE (vnp_txn_ref),
    CONSTRAINT ck_payments_payment_method CHECK (payment_method IN ('VNPAY', 'COD')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_payments_refunded_amount_nonneg CHECK (refunded_amount >= 0)
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_customer_id ON payments (customer_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_vnp_txn_ref ON payments (vnp_txn_ref);

CREATE TABLE payment_transaction_logs (
    id               BIGSERIAL PRIMARY KEY,
    payment_id       BIGINT,
    type             VARCHAR(30)  NOT NULL,
    request_params   TEXT,
    response_params  TEXT,
    response_code    VARCHAR(10),
    message          VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_logs_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT ck_txn_logs_type CHECK (type IN ('CREATE_PAYMENT_URL', 'RETURN_URL', 'IPN_CALLBACK', 'QUERY_TRANSACTION', 'REFUND'))
);

CREATE INDEX idx_txn_logs_payment_id ON payment_transaction_logs (payment_id);

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50)   NOT NULL,
    aggregate_id    VARCHAR(50)   NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    topic           VARCHAR(100)  NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,

    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_status ON outbox_events (status);
