-- ============================================================================
-- V5: Policy Module Tables
-- ============================================================================
-- Layer 4: RBAC/ABAC policy engine
-- Last Updated: 2025-10-25
-- ============================================================================

-- ============================================================================
-- TABLE: common_policy
-- ============================================================================
CREATE TABLE common_policy.common_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    policy_id VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    effect VARCHAR(10) NOT NULL DEFAULT 'DENY',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    
    conditions JSONB DEFAULT '{}'::jsonb,
    description TEXT,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_policy_id ON common_policy.common_policy(policy_id);
CREATE INDEX idx_policy_resource ON common_policy.common_policy(resource);
CREATE INDEX idx_policy_priority ON common_policy.common_policy(priority DESC);
CREATE INDEX idx_policy_enabled ON common_policy.common_policy(enabled) WHERE enabled = TRUE;

COMMENT ON TABLE common_policy.common_policy IS 'Layer 4: RBAC/ABAC policy definitions';
COMMENT ON COLUMN common_policy.common_policy.effect IS 'ALLOW or DENY - DENY always wins!';
COMMENT ON COLUMN common_policy.common_policy.priority IS 'Higher = evaluated first';
COMMENT ON COLUMN common_policy.common_policy.conditions IS 'JSONB: roles, departments, timeRange, fieldConditions';

