-- ═══════════════════════════════════════════════════════════════════════════
-- V039: Create Trading Partner Tables
-- ═══════════════════════════════════════════════════════════════════════════
-- Creates TradingPartnerRegistry (platform-level) and TradingPartner (tenant-level)
-- for B2B partner management with cross-tenant linking support.
--
-- Architecture:
-- - TradingPartnerRegistry: Platform-wide golden record (no tenant_id)
-- - TradingPartner: Tenant-specific relationship (extends BaseEntity pattern)
--
-- Key Features:
-- - Cross-tenant partner deduplication via tax_id + country
-- - linked_tenant_id for cross-platform partner visibility
-- - legacy_company_id for migration traceability from Company table
--
-- PostgreSQL Version: Compatible with PG 14+ (uses partial unique index)
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 1: TradingPartnerRegistry (Platform-level, NO tenant_id)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS common_company.trading_partner_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- Company identification
    -- tax_id is nullable for foreign/unregistered partners
    tax_id VARCHAR(50),
    official_name VARCHAR(255) NOT NULL,
    country VARCHAR(3),  -- ISO 3166-1 alpha-3 (e.g., TUR, USA, DEU)
    
    -- Verification status
    verified_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    linked_tenant_id UUID,  -- Cross-platform link to tenant
    verification_date TIMESTAMP,
    verified_by UUID,
    
    -- Standard fields (no tenant_id - platform-level)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- FK: linked_tenant_id → Company (will change to Tenant in Faz 2)
    -- NOTE: This FK will be updated when Tenant entity is extracted
    CONSTRAINT fk_tpr_linked_tenant FOREIGN KEY (linked_tenant_id)
        REFERENCES common_company.common_company(id) ON DELETE SET NULL
);

-- PG 14 compatible: Partial unique index for tax_id + country
-- Only enforces uniqueness when tax_id IS NOT NULL
-- NULL tax_id entries are allowed to duplicate (cannot be matched)
CREATE UNIQUE INDEX IF NOT EXISTS uk_tpr_tax_country 
    ON common_company.trading_partner_registry(tax_id, country)
    WHERE tax_id IS NOT NULL;

-- Additional indexes
CREATE INDEX IF NOT EXISTS idx_tpr_tax_id 
    ON common_company.trading_partner_registry(tax_id) 
    WHERE tax_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tpr_linked_tenant 
    ON common_company.trading_partner_registry(linked_tenant_id) 
    WHERE linked_tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tpr_name_country 
    ON common_company.trading_partner_registry(official_name, country) 
    WHERE tax_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_tpr_verified 
    ON common_company.trading_partner_registry(verified_status);

CREATE INDEX IF NOT EXISTS idx_tpr_active 
    ON common_company.trading_partner_registry(is_active) 
    WHERE is_active = TRUE;

COMMENT ON TABLE common_company.trading_partner_registry IS 
'Platform-level golden record for trading partners. Enables cross-tenant deduplication via tax_id + country. No tenant_id - this is platform-wide.';

COMMENT ON COLUMN common_company.trading_partner_registry.tax_id IS 
'Tax identification number. Nullable for foreign/unregistered partners. When NOT NULL, forms unique constraint with country.';

COMMENT ON COLUMN common_company.trading_partner_registry.linked_tenant_id IS 
'If partner is also a platform tenant, link to their tenant ID. Enables cross-tenant visibility.';


-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 2: TradingPartner (Tenant-level, follows BaseEntity pattern)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS common_company.common_trading_partner (
    -- BaseEntity standard fields
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- Registry link (golden record reference)
    registry_id UUID NOT NULL,
    
    -- Relationship data
    custom_name VARCHAR(255),  -- Tenant's alias for this partner
    partner_type VARCHAR(30) NOT NULL,  -- SUPPLIER, CUSTOMER, FASON, SERVICE_PROVIDER, BOTH
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INVITED, PENDING_APPROVAL, SUSPENDED, BLOCKED
    
    -- Relationship metadata (JSONB)
    -- Examples: payment_terms, credit_limit, discount_rate, contact_person, notes
    relationship_meta JSONB,
    
    -- Migration traceability
    -- Links to original Company.id for FK updates during transition
    legacy_company_id UUID,
    
    -- BaseEntity audit fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- FK: tenant_id → Company (will change to Tenant in Faz 2)
    CONSTRAINT fk_tp_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_company.common_company(id) ON DELETE RESTRICT,
    
    -- FK: registry_id → TradingPartnerRegistry
    CONSTRAINT fk_tp_registry FOREIGN KEY (registry_id)
        REFERENCES common_company.trading_partner_registry(id) ON DELETE RESTRICT,
    
    -- One relationship per tenant-registry pair
    -- Prevents duplicate entries; use partner_type=BOTH for multi-role
    CONSTRAINT uk_tp_tenant_registry UNIQUE (tenant_id, registry_id)
);

-- Standard indexes (following BaseEntity pattern)
CREATE INDEX IF NOT EXISTS idx_tp_tenant 
    ON common_company.common_trading_partner(tenant_id);

CREATE INDEX IF NOT EXISTS idx_tp_registry 
    ON common_company.common_trading_partner(registry_id);

CREATE INDEX IF NOT EXISTS idx_tp_type 
    ON common_company.common_trading_partner(partner_type);

CREATE INDEX IF NOT EXISTS idx_tp_status 
    ON common_company.common_trading_partner(status);

CREATE INDEX IF NOT EXISTS idx_tp_legacy 
    ON common_company.common_trading_partner(legacy_company_id) 
    WHERE legacy_company_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tp_tenant_active 
    ON common_company.common_trading_partner(tenant_id) 
    WHERE is_active = TRUE;

-- GIN index for JSONB queries on relationship_meta
CREATE INDEX IF NOT EXISTS idx_tp_meta 
    ON common_company.common_trading_partner USING GIN (relationship_meta);

COMMENT ON TABLE common_company.common_trading_partner IS 
'Tenant-specific trading partner relationships. Links to registry for cross-tenant deduplication. Follows BaseEntity pattern.';

COMMENT ON COLUMN common_company.common_trading_partner.registry_id IS 
'Reference to platform-level golden record. Multiple tenants can reference same registry.';

COMMENT ON COLUMN common_company.common_trading_partner.custom_name IS 
'Tenant-specific alias. If NULL, display registry.official_name.';

COMMENT ON COLUMN common_company.common_trading_partner.partner_type IS 
'Relationship type: SUPPLIER, CUSTOMER, FASON, SERVICE_PROVIDER, or BOTH for multi-role.';

COMMENT ON COLUMN common_company.common_trading_partner.legacy_company_id IS 
'Original Company.id from migration. Enables dual-read during transition. Will be dropped in Faz 2.';


-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 3: Legacy Mapping View (Transition period helper)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW common_company.v_partner_legacy_mapping AS
SELECT 
    tp.id AS trading_partner_id,
    tp.uid AS trading_partner_uid,
    tp.tenant_id,
    tp.legacy_company_id,
    tp.partner_type,
    tp.status,
    tp.custom_name,
    r.id AS registry_id,
    r.uid AS registry_uid,
    r.tax_id,
    r.official_name,
    r.country,
    r.linked_tenant_id,
    r.verified_status,
    COALESCE(tp.custom_name, r.official_name) AS display_name,
    tp.is_active,
    tp.created_at
FROM common_company.common_trading_partner tp
JOIN common_company.trading_partner_registry r ON tp.registry_id = r.id;

COMMENT ON VIEW common_company.v_partner_legacy_mapping IS 
'Transition helper: Maps legacy Company.id → new TradingPartner.id. Use for dual-read queries during migration. Remove after Faz 2 completion.';


-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK (commented - run manually if needed)
-- ═══════════════════════════════════════════════════════════════════════════
-- WARNING: Only drops new tables - does NOT affect Company data
--
-- DROP VIEW IF EXISTS common_company.v_partner_legacy_mapping;
-- DROP TABLE IF EXISTS common_company.common_trading_partner CASCADE;
-- DROP TABLE IF EXISTS common_company.trading_partner_registry CASCADE;
