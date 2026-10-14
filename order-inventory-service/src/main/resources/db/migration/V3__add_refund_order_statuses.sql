-- V3: them trang thai don PARTIALLY_REFUNDED / REFUNDED (luong payment.refunded: Payment -> Outbox -> Kafka -> Order).
-- Hibernate ddl-auto=validate khong kiem tra CHECK constraint, nhung DB se tu choi UPDATE status neu khong mo rong constraint.
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (status IN
    ('PENDING', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED'));
