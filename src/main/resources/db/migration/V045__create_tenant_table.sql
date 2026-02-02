-- ═══════════════════════════════════════════════════════════════════════════
-- V045__create_tenant_table.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Creates the Tenant entity - Platform-level subscription and settings.
--
-- This migration separates Tenant from Company:
--   - Tenant: Platform subscription boundary (billing, settings, status)
--   - Organization (Company): Internal structure (departments, hierarchy)
--
-- Architecture:
--   PLATFORM LEVEL (no tenant_id - global)
--   ├── common_tenant (this migration)
--   └── common_trading_partner_registry
--
--   TENANT LEVEL (tenant_id scoped)
--   ├── common_organization (renamed from common_company in V046)
--   ├── common_trading_partner
--   └── All other business entities
--
-- Risk: LOW - additive change, no existing data modified
-- ═══════════════════════════════════════════════════════════════════════════

-- ============================================================================
-- SCHEMA: common_tenant
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS common_tenant;

COMMENT ON SCHEMA common_tenant IS 'Platform-level tenant management: subscriptions, settings, status';

-- ============================================================================
-- TABLE: common_tenant
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_tenant.common_tenant (
    -- Identity
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(50) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    
    -- Basic Info
    name VARCHAR(255) NOT NULL,
    billing_email VARCHAR(255),
    
    -- Subscription Status
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    trial_ends_at TIMESTAMP,
    subscription_plan VARCHAR(50),
    
    -- Settings (JSONB)
    settings JSONB NOT NULL DEFAULT '{
        "timezone": "UTC",
        "locale": "en-US",
        "currency": "USD",
        "betaFeaturesEnabled": false,
        "aiEnabled": true,
        "emailNotificationsEnabled": true,
        "mfaRequired": false,
        "sessionTimeoutMinutes": 480
    }'::jsonb,
    
    -- External Integrations
    stripe_customer_id VARCHAR(100),
    
    -- Audit Fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT uk_tenant_uid UNIQUE (uid),
    CONSTRAINT uk_tenant_slug UNIQUE (slug),
    CONSTRAINT chk_tenant_status CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'))
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Primary lookup indexes
CREATE INDEX IF NOT EXISTS idx_tenant_status ON common_tenant.common_tenant(status);
CREATE INDEX IF NOT EXISTS idx_tenant_active ON common_tenant.common_tenant(is_active) WHERE is_active = TRUE;

-- Trial expiry queries (for scheduled job)
CREATE INDEX IF NOT EXISTS idx_tenant_trial_expiry
    ON common_tenant.common_tenant(status, trial_ends_at)
    WHERE status = 'TRIAL' AND trial_ends_at IS NOT NULL;

-- Billing integration lookup
CREATE INDEX IF NOT EXISTS idx_tenant_stripe
    ON common_tenant.common_tenant(stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

-- ============================================================================
-- COMMENTS
-- ============================================================================
COMMENT ON TABLE common_tenant.common_tenant IS 'Platform-level tenant: subscription, settings, billing';

COMMENT ON COLUMN common_tenant.common_tenant.id IS 'Primary key - used as tenant_id in all tenant-scoped entities';
COMMENT ON COLUMN common_tenant.common_tenant.uid IS 'Human-readable identifier (e.g., ACME-001) for UID generation in child entities';
COMMENT ON COLUMN common_tenant.common_tenant.slug IS 'URL-friendly slug for subdomain routing (e.g., acme-corp)';
COMMENT ON COLUMN common_tenant.common_tenant.status IS 'Lifecycle status: TRIAL → ACTIVE → SUSPENDED → CANCELLED';
COMMENT ON COLUMN common_tenant.common_tenant.trial_ends_at IS 'Trial period end date (null if not in trial)';
COMMENT ON COLUMN common_tenant.common_tenant.settings IS 'Tenant-wide settings: timezone, locale, currency, feature flags, branding';
COMMENT ON COLUMN common_tenant.common_tenant.stripe_customer_id IS 'Stripe customer ID for billing integration';

-- ============================================================================
-- NOTE ON MIGRATION STRATEGY
-- ============================================================================
-- V046 will:
--   1. Migrate existing root companies to this table
--   2. Update tenant_id FKs to reference common_tenant
--   3. Rename common_company to common_organization
--   4. Remove the tenant_id = company_id hack
-- ═══════════════════════════════════════════════════════════════════════════
