-- Luu W3C traceparent de noi trace request -> outbox -> Kafka consumer (nullable, event cu = NULL)
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS trace_parent VARCHAR(64);
