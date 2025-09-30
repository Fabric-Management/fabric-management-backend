-- =============================================================================
-- COMPANY SERVICE DATABASE MIGRATION
-- =============================================================================
-- Creates tables for company management service

-- Companies table
CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY,
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

-- Company events table (for event sourcing)
CREATE TABLE IF NOT EXISTS company_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    event_version INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_event_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Company users table (many-to-many relationship)
CREATE TABLE IF NOT EXISTS company_users (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    CONSTRAINT fk_company_user_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uk_company_user UNIQUE (company_id, user_id)
);

-- Company settings table (for complex settings)
CREATE TABLE IF NOT EXISTS company_settings (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_setting_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uk_company_setting UNIQUE (company_id, setting_key)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_companies_tenant_id ON companies (tenant_id);
CREATE INDEX IF NOT EXISTS idx_companies_name ON companies (name);
CREATE INDEX IF NOT EXISTS idx_companies_type ON companies (type);
CREATE INDEX IF NOT EXISTS idx_companies_industry ON companies (industry);
CREATE INDEX IF NOT EXISTS idx_companies_status ON companies (status);
CREATE INDEX IF NOT EXISTS idx_companies_active ON companies (is_active);
CREATE INDEX IF NOT EXISTS idx_companies_deleted ON companies (deleted);
CREATE INDEX IF NOT EXISTS idx_companies_created_at ON companies (created_at);

CREATE INDEX IF NOT EXISTS idx_events_company_id ON company_events (company_id);
CREATE INDEX IF NOT EXISTS idx_events_type ON company_events (event_type);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON company_events (created_at);

CREATE INDEX IF NOT EXISTS idx_company_users_company_id ON company_users (company_id);
CREATE INDEX IF NOT EXISTS idx_company_users_user_id ON company_users (user_id);
CREATE INDEX IF NOT EXISTS idx_company_users_active ON company_users (is_active);

CREATE INDEX IF NOT EXISTS idx_company_settings_company_id ON company_settings (company_id);
CREATE INDEX IF NOT EXISTS idx_company_settings_key ON company_settings (setting_key);

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_set_updated_at_companies ON companies;
CREATE TRIGGER trg_set_updated_at_companies
  BEFORE UPDATE ON companies
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_set_updated_at_company_settings ON company_settings;
CREATE TRIGGER trg_set_updated_at_company_settings
  BEFORE UPDATE ON company_settings
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();
