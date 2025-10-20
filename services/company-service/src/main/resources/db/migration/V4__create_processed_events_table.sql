-- =============================================================================
-- IDEMPOTENCY CHECK TABLE
-- =============================================================================
-- Purpose: Prevent duplicate event processing (at-least-once → exactly-once)
-- Pattern: Event Deduplication (Google/Amazon/Netflix standard)
--
-- How it works:
-- 1. Before processing Kafka event, check if event_id exists
-- 2. If exists → skip (already processed)
-- 3. If not exists → process + insert event_id atomically
-- 4. Ensures exactly-once semantics
--
-- Created: 2025-10-20
-- =============================================================================

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,                  -- Unique event ID from Kafka
    event_type VARCHAR(100) NOT NULL,           -- e.g., 'CompanyCreatedEvent'
    aggregate_id UUID NOT NULL,                 -- Entity ID (companyId, contactId)
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id UUID NOT NULL,
    
    -- For auditing and debugging
    source_service VARCHAR(50) NOT NULL,        -- e.g., 'company-service'
    kafka_topic VARCHAR(100) NOT NULL,
    kafka_partition INTEGER,
    kafka_offset BIGINT
);

-- Index for fast duplicate detection (most important query)
CREATE UNIQUE INDEX idx_processed_events_event_id 
ON processed_events(event_id);

-- Index for cleanup/monitoring queries
CREATE INDEX idx_processed_events_processed_at 
ON processed_events(processed_at);

-- Index for tenant isolation
CREATE INDEX idx_processed_events_tenant 
ON processed_events(tenant_id);

-- Index for aggregate lookup (debugging)
CREATE INDEX idx_processed_events_aggregate 
ON processed_events(event_type, aggregate_id);

COMMENT ON TABLE processed_events IS 'Idempotency check - prevents duplicate Kafka event processing';
COMMENT ON COLUMN processed_events.event_id IS 'Unique event ID from source service (must be globally unique)';
COMMENT ON COLUMN processed_events.kafka_offset IS 'Kafka offset for debugging (optional)';

-- Auto-cleanup old events (optional, for production)
-- COMMENT: Add a cron job or scheduled task to delete events older than 30 days
-- DELETE FROM processed_events WHERE processed_at < NOW() - INTERVAL '30 days';

