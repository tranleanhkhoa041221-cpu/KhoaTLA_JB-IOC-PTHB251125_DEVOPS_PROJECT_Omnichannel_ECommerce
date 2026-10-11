CREATE TABLE notification_logs (
    id               BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT          NOT NULL,
    order_id         BIGINT,
    order_code       VARCHAR(40),
    notification_type VARCHAR(50)    NOT NULL,
    channel          VARCHAR(20)     NOT NULL,
    recipient        VARCHAR(200)    NOT NULL,
    subject          VARCHAR(500),
    body             TEXT            NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'SENT',
    error_message    VARCHAR(1000),
    idempotency_key  VARCHAR(120)    NOT NULL,
    kafka_topic      VARCHAR(80),
    event_type       VARCHAR(80),
    sent_at          TIMESTAMP,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notification_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_notification_logs_type CHECK (notification_type IN
        ('ORDER_CREATED', 'ORDER_CANCELLED', 'PAYMENT_COMPLETED', 'PAYMENT_FAILED', 'MANUAL_TEST')),
    CONSTRAINT ck_notification_logs_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT ck_notification_logs_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notification_logs_customer_id ON notification_logs (customer_id);
CREATE INDEX idx_notification_logs_order_id ON notification_logs (order_id);
CREATE INDEX idx_notification_logs_type ON notification_logs (notification_type);
CREATE INDEX idx_notification_logs_created_at ON notification_logs (created_at DESC);
