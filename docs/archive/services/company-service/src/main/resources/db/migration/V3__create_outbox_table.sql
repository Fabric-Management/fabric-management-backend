-- =============================================================================
-- Migration V3: OUTBOX PATTERN TABLE
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id UUID NOT NULL,
    trace_id VARCHAR(100),
    correlation_id VARCHAR(100),
    headers JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    published_at TIMESTAMPTZ,
    
    CONSTRAINT outbox_events_status_check CHECK (status IN ('NEW', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT outbox_events_version_check CHECK (event_version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$')
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_occurred 
ON outbox_events(status, occurred_at) 
WHERE status = 'NEW';

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate 
ON outbox_events(aggregate_type, aggregate_id);

CREATE INDEX IF NOT EXISTS idx_outbox_tenant 
ON outbox_events(tenant_id);

CREATE INDEX IF NOT EXISTS idx_outbox_trace 
ON outbox_events(trace_id) 
WHERE trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_published_cleanup 
ON outbox_events(published_at) 
WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS idx_outbox_failed_monitoring 
ON outbox_events(occurred_at) 
WHERE status = 'FAILED';