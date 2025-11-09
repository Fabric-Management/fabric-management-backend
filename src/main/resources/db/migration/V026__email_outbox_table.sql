-- ============================================================================
-- V026: Email Outbox Table - Transactional Outbox Pattern
-- ============================================================================
-- Implements Transactional Outbox pattern for reliable email delivery
-- Ensures email persistence, retry capability, and dead letter queue
-- Last Updated: 2025-11-06
-- ============================================================================

-- ============================================================================
-- TABLE: communication_email_outbox
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.communication_email_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    html_body TEXT NOT NULL,
    
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    last_error TEXT,
    sent_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_email_outbox_status ON common_communication.communication_email_outbox(status);
CREATE INDEX IF NOT EXISTS idx_email_outbox_created_at ON common_communication.communication_email_outbox(created_at);
CREATE INDEX IF NOT EXISTS idx_email_outbox_tenant ON common_communication.communication_email_outbox(tenant_id);
CREATE INDEX IF NOT EXISTS idx_email_outbox_next_retry ON common_communication.communication_email_outbox(next_retry_at) 
    WHERE next_retry_at IS NOT NULL;

-- Status check constraint
ALTER TABLE common_communication.communication_email_outbox 
    ADD CONSTRAINT chk_email_outbox_status 
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'));

-- Comments
COMMENT ON TABLE common_communication.communication_email_outbox IS 
    'Transactional Outbox pattern: Email queue for reliable delivery with retry and dead letter queue support';
COMMENT ON COLUMN common_communication.communication_email_outbox.status IS 
    'Email status: PENDING (queued), SENDING (processing), SENT (success), FAILED (dead letter)';
COMMENT ON COLUMN common_communication.communication_email_outbox.retry_count IS 
    'Number of retry attempts (exponential backoff: 1s, 2s, 4s)';
COMMENT ON COLUMN common_communication.communication_email_outbox.max_retries IS 
    'Maximum retry attempts before moving to dead letter queue (default: 3)';
COMMENT ON COLUMN common_communication.communication_email_outbox.next_retry_at IS 
    'Next retry timestamp (exponential backoff scheduling)';

