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

-- =============================================================================
-- CONTACTS TABLE (Email, Phone, etc.)
-- =============================================================================
-- Stores simple contact information (email, phone)
-- For ADDRESS type, actual address data is in 'addresses' table
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    owner_type VARCHAR(50) NOT NULL,
    
    -- Contact data (for EMAIL, PHONE, etc.)
    contact_value VARCHAR(500),  -- NULL for ADDRESS type
    contact_type VARCHAR(50) NOT NULL,
    
    -- Parent relationship (for extensions)
    parent_contact_id UUID,  -- For PHONE_EXTENSION → links to company's main phone
    
    -- Verification
    is_verified BOOLEAN DEFAULT FALSE,
    is_primary BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    verification_code VARCHAR(10),
    verification_expires_at TIMESTAMP,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    -- Foreign key constraint
    CONSTRAINT fk_parent_contact FOREIGN KEY (parent_contact_id) 
        REFERENCES contacts(id) ON DELETE SET NULL
);

-- Contact indexes
CREATE INDEX IF NOT EXISTS idx_contacts_owner_id ON contacts(owner_id);
CREATE INDEX IF NOT EXISTS idx_contacts_owner_type ON contacts(owner_type);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_value ON contacts(contact_value);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_type ON contacts(contact_type);
CREATE INDEX IF NOT EXISTS idx_contacts_is_verified ON contacts(is_verified);
CREATE INDEX IF NOT EXISTS idx_contacts_is_primary ON contacts(is_primary);
CREATE INDEX IF NOT EXISTS idx_contacts_owner_id_type ON contacts(owner_id, owner_type);
CREATE INDEX IF NOT EXISTS idx_contacts_parent_id ON contacts(parent_contact_id);
CREATE INDEX IF NOT EXISTS idx_contacts_deleted ON contacts(deleted);

-- Add unique constraint for contact value (idempotent)
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_contact_value'
    ) THEN
        ALTER TABLE contacts ADD CONSTRAINT uk_contact_value UNIQUE (contact_value);
    END IF;
END $$;

-- Add check constraints (idempotent)
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_owner_type'
    ) THEN
        ALTER TABLE contacts ADD CONSTRAINT chk_owner_type
            CHECK (owner_type IN ('USER', 'COMPANY'));
    END IF;
END $$;

DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_contact_type'
    ) THEN
        ALTER TABLE contacts ADD CONSTRAINT chk_contact_type
            CHECK (contact_type IN ('EMAIL', 'PHONE', 'PHONE_EXTENSION', 'ADDRESS', 'FAX', 'WEBSITE', 'SOCIAL_MEDIA'));
    END IF;
END $$;

-- =============================================================================
-- ADDRESSES TABLE (Complex address data)
-- =============================================================================
-- Separate table for ADDRESS contact type
-- Linked to contacts table via contact_id
CREATE TABLE IF NOT EXISTS addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL,
    owner_id UUID NOT NULL,  -- Denormalized for fast queries
    owner_type VARCHAR(50) NOT NULL,
    
    -- Address fields
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    district VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) NOT NULL,
    
    -- Google Places integration (optional)
    google_place_id VARCHAR(255),
    formatted_address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    
    -- Metadata
    address_type VARCHAR(50) DEFAULT 'HOME',  -- HOME, WORK, BILLING, SHIPPING
    is_primary BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    -- Foreign keys
    CONSTRAINT fk_addresses_contact FOREIGN KEY (contact_id) 
        REFERENCES contacts(id) ON DELETE CASCADE
);

-- Address indexes
CREATE INDEX IF NOT EXISTS idx_addresses_contact_id ON addresses(contact_id);
CREATE INDEX IF NOT EXISTS idx_addresses_owner_id ON addresses(owner_id);
CREATE INDEX IF NOT EXISTS idx_addresses_owner_type ON addresses(owner_type);
CREATE INDEX IF NOT EXISTS idx_addresses_owner_id_type ON addresses(owner_id, owner_type);
CREATE INDEX IF NOT EXISTS idx_addresses_country ON addresses(country);
CREATE INDEX IF NOT EXISTS idx_addresses_postal_code ON addresses(postal_code);
CREATE INDEX IF NOT EXISTS idx_addresses_deleted ON addresses(deleted);

-- Address type constraint
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_address_type'
    ) THEN
        ALTER TABLE addresses ADD CONSTRAINT chk_address_type
            CHECK (address_type IN ('HOME', 'WORK', 'BILLING', 'SHIPPING'));
    END IF;
END $$;

-- =============================================================================
-- TRIGGERS (Auto-update timestamps)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_set_updated_at_contacts ON contacts;
CREATE TRIGGER trg_set_updated_at_contacts
  BEFORE UPDATE ON contacts
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_set_updated_at_addresses ON addresses;
CREATE TRIGGER trg_set_updated_at_addresses
  BEFORE UPDATE ON addresses
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

-- =============================================================================
-- SEED DATA
-- =============================================================================
-- No default contacts - all contacts created via tenant onboarding or user creation
