-- =============================================================================
-- Migration V4: OUTBOX PATTERN TABLE
-- =============================================================================
-- Purpose: Guarantees event delivery even if Kafka is down
-- Pattern: Transactional Outbox (Google/Amazon/Netflix standard)
--
-- How it works:
-- 1. Business transaction writes to domain table + outbox table (same txn)
-- 2. Background publisher polls outbox and sends to Kafka
-- 3. Marks as PUBLISHED after successful send
-- 4. Ensures at-least-once delivery guarantee
--
-- Created: 2025-10-20
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,        -- e.g., 'USER', 'COMPANY'
    aggregate_id UUID NOT NULL,                 -- Entity ID (userId, companyId)
    event_type VARCHAR(100) NOT NULL,           -- e.g., 'UserCreatedEvent'
    payload JSONB NOT NULL,                     -- Event data as JSON
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    topic VARCHAR(100) NOT NULL,                -- Kafka topic name
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    tenant_id UUID NOT NULL,
    
    -- Indexes for performance
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Index for polling pending events (most important query)
CREATE INDEX idx_outbox_events_status_created 
ON outbox_events(status, created_at) 
WHERE status = 'PENDING';

-- Index for aggregate lookup
CREATE INDEX idx_outbox_events_aggregate 
ON outbox_events(aggregate_type, aggregate_id);

-- Index for tenant isolation
CREATE INDEX idx_outbox_events_tenant 
ON outbox_events(tenant_id);

-- Partial index for failed events (for monitoring)
CREATE INDEX idx_outbox_events_failed 
ON outbox_events(created_at) 
WHERE status = 'FAILED';

COMMENT ON TABLE outbox_events IS 'Transactional Outbox Pattern - guarantees event delivery to Kafka';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Domain entity type (USER, COMPANY, etc.)';
COMMENT ON COLUMN outbox_events.status IS 'PENDING: not sent, PUBLISHED: sent successfully, FAILED: send failed after retries';
COMMENT ON COLUMN outbox_events.retry_count IS 'Number of publish attempts (max 3)';

