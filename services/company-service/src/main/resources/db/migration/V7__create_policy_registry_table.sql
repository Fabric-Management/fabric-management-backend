-- ============================================================================
-- Migration V7: Create Policy Registry Table
-- Feature: Policy Authorization System
-- Description: Endpoint security catalog and platform policy definitions
-- 
-- Purpose:
-- - Centralized endpoint security catalog
-- - Define allowed company types per endpoint
-- - Define default roles
-- - Store platform policies (JSON)
-- 
-- Dependencies: None
-- ============================================================================

CREATE TABLE IF NOT EXISTS policy_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint VARCHAR(200) NOT NULL UNIQUE,
    operation VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    allowed_company_types TEXT[],
    default_roles TEXT[],
    requires_grant BOOLEAN NOT NULL DEFAULT FALSE,
    platform_policy JSONB,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    policy_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_registry_operation CHECK (operation IN ('READ', 'WRITE', 'DELETE', 'APPROVE', 'EXPORT', 'MANAGE')),
    CONSTRAINT chk_registry_scope CHECK (scope IN ('SELF', 'COMPANY', 'CROSS_COMPANY', 'GLOBAL'))
);

COMMENT ON TABLE policy_registry IS 'Policy registry - Endpoint security catalog (V7)';
COMMENT ON COLUMN policy_registry.endpoint IS 'Endpoint pattern. Example: /api/v1/users';
COMMENT ON COLUMN policy_registry.policy_version IS 'Policy version. Auto-incremented on update. Format: v1, v1.1, v1.2';
COMMENT ON COLUMN policy_registry.version IS 'Optimistic locking version (from BaseEntity)';
COMMENT ON COLUMN policy_registry.active IS 'If FALSE, policy is disabled (fallback to deny)';

CREATE UNIQUE INDEX uk_registry_endpoint ON policy_registry(endpoint) WHERE active = TRUE;
CREATE INDEX idx_registry_lookup ON policy_registry(endpoint, operation, active) WHERE active = TRUE;
CREATE INDEX idx_registry_allowed_types ON policy_registry USING GIN (allowed_company_types);
CREATE INDEX idx_registry_default_roles ON policy_registry USING GIN (default_roles);

CREATE TRIGGER update_policy_registry_updated_at
    BEFORE UPDATE ON policy_registry
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

