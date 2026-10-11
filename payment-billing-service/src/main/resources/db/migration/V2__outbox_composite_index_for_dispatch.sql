-- ============================================================================
-- V2: Performance/Scalability - toi uu query dispatch cua Outbox Scheduler.
-- Xem giai thich chi tiet trong V2 cung ten cua order-inventory-service (cung ly do,
-- cung truy van WHERE status = 'PENDING' ORDER BY id ASC LIMIT 200).
-- ============================================================================

DROP INDEX IF EXISTS idx_outbox_status;
CREATE INDEX idx_outbox_status_id ON outbox_events (status, id);
