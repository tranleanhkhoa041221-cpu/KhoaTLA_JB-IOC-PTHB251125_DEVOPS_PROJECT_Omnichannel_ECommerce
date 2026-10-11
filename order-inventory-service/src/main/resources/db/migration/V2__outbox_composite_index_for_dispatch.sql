-- ============================================================================
-- V2: Performance/Scalability - toi uu query dispatch cua Outbox Scheduler.
--
-- OutboxEventPublisher.publishPendingEvents() chay moi 1 giay va goi
-- findTop200ByStatusOrderByIdAsc(PENDING), tuc la:
--   WHERE status = 'PENDING' ORDER BY id ASC LIMIT 200
-- Index don cot idx_outbox_status (status) cu chi giup loc nhanh theo status, nhung Postgres
-- van phai SAP XEP (sort) tap ket qua da loc theo id truoc khi LIMIT. Doi thanh index 2 cot
-- (status, id) de Postgres co the duyet THANG theo thu tu id da co san trong index (index range
-- scan), khong can buoc sort rieng - quan trong hon khi vua tang gioi han tu Top50 len Top200/tick
-- (OutboxEventPublisher) de dap ung Flash Sale 500-1000 req/s.
-- ============================================================================

DROP INDEX IF EXISTS idx_outbox_status;
CREATE INDEX idx_outbox_status_id ON outbox_events (status, id);
