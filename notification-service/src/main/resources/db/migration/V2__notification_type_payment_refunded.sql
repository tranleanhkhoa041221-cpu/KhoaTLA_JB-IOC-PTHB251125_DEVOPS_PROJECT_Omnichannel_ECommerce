ALTER TABLE notification_logs DROP CONSTRAINT ck_notification_logs_type;
ALTER TABLE notification_logs ADD CONSTRAINT ck_notification_logs_type CHECK (notification_type IN
    ('ORDER_CREATED', 'ORDER_CANCELLED', 'PAYMENT_COMPLETED', 'PAYMENT_FAILED', 'PAYMENT_REFUNDED', 'MANUAL_TEST'));
