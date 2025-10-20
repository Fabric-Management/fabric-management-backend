-- =============================================================================
-- OUTBOX PATTERN TABLE
-- =============================================================================
-- Purpose: Guarantees event delivery even if Kafka is down
-- Pattern: Transactional Outbox (Google/Amazon/Netflix standard)
-- Created: 2025-10-20
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    topic VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    tenant_id UUID NOT NULL,
    
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created ON outbox_events(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_events_tenant ON outbox_events(tenant_id);
CREATE INDEX idx_outbox_events_failed ON outbox_events(created_at) WHERE status = 'FAILED';

COMMENT ON TABLE outbox_events IS 'Transactional Outbox Pattern - guarantees event delivery to Kafka';

