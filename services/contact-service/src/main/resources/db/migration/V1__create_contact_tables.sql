-- =============================================================================
-- CONTACT SERVICE DATABASE MIGRATION
-- =============================================================================
-- Creates tables for contact management service

-- Contacts table
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID,
    company_id UUID,
    type VARCHAR(20) NOT NULL CHECK (type IN ('USER', 'COMPANY', 'SHARED')),
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    display_name VARCHAR(100),
    primary_email VARCHAR(100) NOT NULL,
    primary_phone VARCHAR(20),
    primary_address JSONB,
    additional_emails JSONB,
    additional_phones JSONB,
    additional_addresses JSONB,
    preferences JSONB,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,
    verification_token VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Constraints
    CONSTRAINT chk_user_or_company CHECK (
        (user_id IS NOT NULL AND company_id IS NULL) OR 
        (user_id IS NULL AND company_id IS NOT NULL) OR
        (user_id IS NOT NULL AND company_id IS NOT NULL AND type = 'SHARED')
    )
);

-- Contact events table (for event sourcing)
CREATE TABLE IF NOT EXISTS contact_events (
    id UUID PRIMARY KEY,
    contact_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    event_version INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_event_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
);

-- Contact verification table
CREATE TABLE IF NOT EXISTS contact_verifications (
    id UUID PRIMARY KEY,
    contact_id UUID NOT NULL,
    verification_type VARCHAR(20) NOT NULL CHECK (verification_type IN ('EMAIL', 'PHONE')),
    verification_value VARCHAR(255) NOT NULL,
    verification_token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_verification_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_contacts_tenant_id ON contacts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON contacts (user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_company_id ON contacts (company_id);
CREATE INDEX IF NOT EXISTS idx_contacts_type ON contacts (type);
CREATE INDEX IF NOT EXISTS idx_contacts_primary_email ON contacts (primary_email);
CREATE INDEX IF NOT EXISTS idx_contacts_primary_phone ON contacts (primary_phone);
CREATE INDEX IF NOT EXISTS idx_contacts_verified ON contacts (is_verified);
CREATE INDEX IF NOT EXISTS idx_contacts_deleted ON contacts (deleted);
CREATE INDEX IF NOT EXISTS idx_contacts_created_at ON contacts (created_at);

CREATE INDEX IF NOT EXISTS idx_events_contact_id ON contact_events (contact_id);
CREATE INDEX IF NOT EXISTS idx_events_type ON contact_events (event_type);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON contact_events (created_at);

CREATE INDEX IF NOT EXISTS idx_verifications_contact_id ON contact_verifications (contact_id);
CREATE INDEX IF NOT EXISTS idx_verifications_token ON contact_verifications (verification_token);
CREATE INDEX IF NOT EXISTS idx_verifications_expires_at ON contact_verifications (expires_at);

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_set_updated_at_contacts ON contacts;
CREATE TRIGGER trg_set_updated_at_contacts
  BEFORE UPDATE ON contacts
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_set_updated_at_verifications ON contact_verifications;
CREATE TRIGGER trg_set_updated_at_verifications
  BEFORE UPDATE ON contact_verifications
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();
