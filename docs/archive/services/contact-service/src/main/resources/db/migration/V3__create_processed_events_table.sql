-- =============================================================================
-- Migration V3: IDEMPOTENCY CHECK TABLE
-- =============================================================================
-- Purpose: Prevent duplicate event processing (at-least-once → exactly-once)
-- Pattern: Event Deduplication (Google/Amazon/Netflix standard)
-- Created: 2025-10-20
-- =============================================================================

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id UUID NOT NULL,
    source_service VARCHAR(50) NOT NULL,
    kafka_topic VARCHAR(100) NOT NULL,
    kafka_partition INTEGER,
    kafka_offset BIGINT
);

CREATE UNIQUE INDEX idx_processed_events_event_id ON processed_events(event_id);
CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);
CREATE INDEX idx_processed_events_tenant ON processed_events(tenant_id);
CREATE INDEX idx_processed_events_aggregate ON processed_events(event_type, aggregate_id);

COMMENT ON TABLE processed_events IS 'Idempotency check - prevents duplicate Kafka event processing';

