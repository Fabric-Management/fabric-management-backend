-- ============================================================================
-- V2: Company Module Tables
-- ============================================================================
-- Company, Department, Subscription, OSDefinition, FeatureCatalog, SubscriptionQuota
-- Last Updated: 2025-10-25
-- ============================================================================

-- ============================================================================
-- TABLE: common_company
-- ============================================================================
CREATE TABLE common_company.common_company (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    company_name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50) UNIQUE NOT NULL,
    address VARCHAR(500),
    city VARCHAR(100),
    country VARCHAR(100),
    phone_number VARCHAR(50),
    email VARCHAR(255),
    company_type VARCHAR(50) NOT NULL DEFAULT 'VERTICAL_MILL',
    parent_company_id UUID,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_company_parent FOREIGN KEY (parent_company_id) 
        REFERENCES common_company.common_company(id) ON DELETE SET NULL
);

CREATE INDEX idx_company_tenant ON common_company.common_company(tenant_id);
CREATE INDEX idx_company_type ON common_company.common_company(company_type);
CREATE INDEX idx_company_tax_id ON common_company.common_company(tax_id);
CREATE INDEX idx_company_active ON common_company.common_company(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_company IS 'Company/Tenant registry - 22 types (SPINNER, WEAVER, etc.)';
COMMENT ON COLUMN common_company.common_company.company_type IS '22 types: SPINNER, WEAVER, KNITTER, DYER_FINISHER, VERTICAL_MILL, etc.';

-- ============================================================================
-- TABLE: common_department
-- ============================================================================
CREATE TABLE common_company.common_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    company_id UUID NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    manager_id UUID,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_department_company FOREIGN KEY (company_id) 
        REFERENCES common_company.common_company(id) ON DELETE CASCADE
);

CREATE INDEX idx_department_tenant ON common_company.common_department(tenant_id);
CREATE INDEX idx_department_company ON common_company.common_department(company_id);
CREATE INDEX idx_department_name ON common_company.common_department(department_name);

COMMENT ON TABLE common_company.common_department IS 'Organizational departments within companies';

-- ============================================================================
-- TABLE: common_os_definition
-- ============================================================================
CREATE TABLE common_company.common_os_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    os_code VARCHAR(50) UNIQUE NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    os_type VARCHAR(20) NOT NULL DEFAULT 'FULL',
    description TEXT,
    included_modules JSONB NOT NULL DEFAULT '[]'::jsonb,
    available_tiers JSONB DEFAULT '[]'::jsonb,
    default_tier VARCHAR(50) DEFAULT 'Professional',
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_os_code ON common_company.common_os_definition(os_code);
CREATE INDEX idx_os_type ON common_company.common_os_definition(os_type);
CREATE INDEX idx_os_active ON common_company.common_os_definition(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_os_definition IS 'OS catalog: YarnOS, LoomOS, FabricOS, etc.';
COMMENT ON COLUMN common_company.common_os_definition.included_modules IS 'JSONB array of module paths (e.g., ["production.yarn", "production.fiber"])';

-- ============================================================================
-- TABLE: common_subscription
-- ============================================================================
CREATE TABLE common_company.common_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    os_code VARCHAR(50) NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    pricing_tier VARCHAR(50),  -- OPTIONAL: Simple OS-based model (no tiers)
    
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    trial_ends_at TIMESTAMP,
    
    features JSONB DEFAULT '{}'::jsonb,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_tenant_os UNIQUE(tenant_id, os_code)
    -- FK constraint removed: Simple model, no OS catalog needed
);

CREATE INDEX idx_subscription_tenant_os ON common_company.common_subscription(tenant_id, os_code);
CREATE INDEX idx_subscription_status ON common_company.common_subscription(status);
CREATE INDEX idx_subscription_expiry ON common_company.common_subscription(expiry_date) WHERE expiry_date IS NOT NULL;

COMMENT ON TABLE common_company.common_subscription IS 'Tenant OS subscriptions - Simple OS-based pricing model';
COMMENT ON COLUMN common_company.common_subscription.pricing_tier IS 'OPTIONAL: For future feature-gating (not used in simple model)';
COMMENT ON COLUMN common_company.common_subscription.features IS 'JSONB map of feature overrides: {"yarn.blend.management": true}';

-- ============================================================================
-- TABLE: common_feature_catalog
-- ============================================================================
CREATE TABLE common_company.common_feature_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    feature_id VARCHAR(100) UNIQUE NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    
    available_in_tiers JSONB NOT NULL DEFAULT '["Enterprise"]'::jsonb,
    requires_os VARCHAR(50),
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_feature_os FOREIGN KEY (os_code) 
        REFERENCES common_company.common_os_definition(os_code) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_feature_id ON common_company.common_feature_catalog(feature_id);
CREATE INDEX idx_feature_os ON common_company.common_feature_catalog(os_code);
CREATE INDEX idx_feature_category ON common_company.common_feature_catalog(category);

COMMENT ON TABLE common_company.common_feature_catalog IS 'Master feature catalog for entitlement checks';
COMMENT ON COLUMN common_company.common_feature_catalog.feature_id IS 'Format: {os_prefix}.{module}.{feature_name} (e.g., "yarn.blend.management")';
COMMENT ON COLUMN common_company.common_feature_catalog.available_in_tiers IS 'JSONB array of tier names: ["Professional", "Enterprise"]';

-- ============================================================================
-- TABLE: common_subscription_quota
-- ============================================================================
CREATE TABLE common_company.common_subscription_quota (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    subscription_id UUID NOT NULL,
    quota_type VARCHAR(50) NOT NULL,
    quota_limit BIGINT NOT NULL,
    quota_used BIGINT NOT NULL DEFAULT 0,
    reset_period VARCHAR(20) NOT NULL DEFAULT 'NONE',
    last_reset_at TIMESTAMP,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_tenant_subscription_quota UNIQUE(tenant_id, subscription_id, quota_type),
    CONSTRAINT fk_quota_subscription FOREIGN KEY (subscription_id) 
        REFERENCES common_company.common_subscription(id) ON DELETE CASCADE
);

CREATE INDEX idx_quota_tenant_type ON common_company.common_subscription_quota(tenant_id, quota_type);
CREATE INDEX idx_quota_subscription ON common_company.common_subscription_quota(subscription_id);
CREATE INDEX idx_quota_reset ON common_company.common_subscription_quota(reset_period, last_reset_at);

COMMENT ON TABLE common_company.common_subscription_quota IS 'Usage limits and quotas per subscription';
COMMENT ON COLUMN common_company.common_subscription_quota.quota_type IS 'Examples: users, api_calls, storage_gb, fiber_entities';
COMMENT ON COLUMN common_company.common_subscription_quota.reset_period IS 'NONE, MONTHLY, DAILY';

