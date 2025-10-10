-- =============================================================================
-- COMPANY SERVICE DATABASE MIGRATION
-- =============================================================================
-- Creates tables for company management service

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

-- Companies table
CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    legal_name VARCHAR(200),
    tax_id VARCHAR(50),
    registration_number VARCHAR(100),
    type VARCHAR(30) NOT NULL CHECK (type IN ('CORPORATION', 'LLC', 'PARTNERSHIP', 'SOLE_PROPRIETORSHIP', 'NON_PROFIT', 'GOVERNMENT', 'OTHER')),
    industry VARCHAR(30) NOT NULL CHECK (industry IN ('TECHNOLOGY', 'MANUFACTURING', 'RETAIL', 'HEALTHCARE', 'FINANCE', 'EDUCATION', 'REAL_ESTATE', 'TRANSPORTATION', 'ENERGY', 'AGRICULTURE', 'CONSTRUCTION', 'HOSPITALITY', 'MEDIA', 'PROFESSIONAL_SERVICES', 'NON_PROFIT', 'GOVERNMENT', 'OTHER')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING', 'DELETED')),
    description TEXT,
    website VARCHAR(255),
    logo_url VARCHAR(500),
    settings JSONB,
    preferences JSONB,
    subscription_start_date TIMESTAMP,
    subscription_end_date TIMESTAMP,
    subscription_plan VARCHAR(50) DEFAULT 'BASIC',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    max_users INTEGER NOT NULL DEFAULT 10,
    current_users INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Removed tables (not needed):
-- - company_events: Event sourcing not implemented, using Outbox Pattern
-- - company_users: Using users.company_id (1-to-1 relationship sufficient)
-- - company_settings: Using companies.settings JSONB instead

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_companies_tenant_id ON companies (tenant_id);
CREATE INDEX IF NOT EXISTS idx_companies_name ON companies (name);
CREATE INDEX IF NOT EXISTS idx_companies_type ON companies (type);
CREATE INDEX IF NOT EXISTS idx_companies_industry ON companies (industry);
CREATE INDEX IF NOT EXISTS idx_companies_status ON companies (status);
CREATE INDEX IF NOT EXISTS idx_companies_active ON companies (is_active);
CREATE INDEX IF NOT EXISTS idx_companies_deleted ON companies (deleted);
CREATE INDEX IF NOT EXISTS idx_companies_created_at ON companies (created_at);

-- =============================================================================
-- TRIGGERS (Auto-update timestamps)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_set_updated_at_companies ON companies;
CREATE TRIGGER trg_set_updated_at_companies
  BEFORE UPDATE ON companies
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- OUTBOX PATTERN TABLE (For reliable event publishing)
-- =============================================================================
-- Service-specific outbox to prevent table name conflicts
CREATE TABLE IF NOT EXISTS company_outbox_events (
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
CREATE INDEX IF NOT EXISTS idx_company_outbox_processed ON company_outbox_events (processed, created_at);
CREATE INDEX IF NOT EXISTS idx_company_outbox_aggregate ON company_outbox_events (aggregate_type, aggregate_id);
