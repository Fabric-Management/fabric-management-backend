-- =============================================================================
-- CONTACT SERVICE DATABASE MIGRATION
-- =============================================================================
-- Creates tables for contact management service

-- =============================================================================
-- COMMON FUNCTIONS (Idempotent - Self-contained)
-- =============================================================================
-- Each migration defines its own dependencies (Microservice Principle)
-- CREATE OR REPLACE ensures idempotency and no conflicts
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- TABLES
-- =============================================================================

-- Contacts table
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id VARCHAR(255) NOT NULL,
    owner_type VARCHAR(50) NOT NULL,
    contact_value VARCHAR(500) NOT NULL,
    contact_type VARCHAR(50) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    is_primary BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    verification_code VARCHAR(10),
    verification_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_contacts_owner_id ON contacts(owner_id);
CREATE INDEX IF NOT EXISTS idx_contacts_owner_type ON contacts(owner_type);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_value ON contacts(contact_value);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_type ON contacts(contact_type);
CREATE INDEX IF NOT EXISTS idx_contacts_is_verified ON contacts(is_verified);
CREATE INDEX IF NOT EXISTS idx_contacts_is_primary ON contacts(is_primary);
CREATE INDEX IF NOT EXISTS idx_contacts_owner_id_type ON contacts(owner_id, owner_type);
CREATE INDEX IF NOT EXISTS idx_contacts_deleted ON contacts(deleted);

-- Add unique constraint for contact value
ALTER TABLE contacts ADD CONSTRAINT uk_contact_value UNIQUE (contact_value);

-- Add check constraints
ALTER TABLE contacts ADD CONSTRAINT chk_owner_type 
    CHECK (owner_type IN ('USER', 'COMPANY'));
    
ALTER TABLE contacts ADD CONSTRAINT chk_contact_type 
    CHECK (contact_type IN ('EMAIL', 'PHONE', 'ADDRESS', 'FAX', 'WEBSITE', 'SOCIAL_MEDIA'));

-- =============================================================================
-- TRIGGERS (Auto-update timestamps)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_set_updated_at_contacts ON contacts;
CREATE TRIGGER trg_set_updated_at_contacts
  BEFORE UPDATE ON contacts
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- OUTBOX PATTERN TABLE (For reliable event publishing)
-- =============================================================================
-- Service-specific outbox to prevent table name conflicts
CREATE TABLE IF NOT EXISTS contact_outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    tenant_id VARCHAR(100),
    topic VARCHAR(100) NOT NULL
);

-- Outbox indexes
CREATE INDEX IF NOT EXISTS idx_contact_outbox_processed ON contact_outbox_events (processed, created_at);
CREATE INDEX IF NOT EXISTS idx_contact_outbox_aggregate ON contact_outbox_events (aggregate_type, aggregate_id);
