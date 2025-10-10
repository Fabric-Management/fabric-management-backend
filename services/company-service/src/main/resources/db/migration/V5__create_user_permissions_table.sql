-- ============================================================================
-- Migration V5: Create User Permissions Table
-- Feature: Policy Authorization System
-- Description: User-specific grants for Advanced Settings
-- 
-- Purpose:
-- - Store endpoint-level permission grants/denies
-- - Support time-bound permissions (TTL)
-- - Enable Admin to grant/revoke specific permissions
-- 
-- Dependencies: None (cross-service reference to users)
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    endpoint VARCHAR(200) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    granted_by UUID,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    CONSTRAINT chk_permissions_operation CHECK (operation IN ('READ', 'WRITE', 'DELETE', 'APPROVE', 'EXPORT', 'MANAGE')),
    CONSTRAINT chk_permissions_scope CHECK (scope IN ('SELF', 'COMPANY', 'CROSS_COMPANY', 'GLOBAL')),
    CONSTRAINT chk_permissions_type CHECK (permission_type IN ('ALLOW', 'DENY')),
    CONSTRAINT chk_permissions_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_permissions_dates CHECK (valid_until IS NULL OR valid_until > valid_from)
);

COMMENT ON TABLE user_permissions IS 'User-specific permission grants (V5)';

-- Indexes (IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_permissions_user ON user_permissions(user_id) WHERE status = 'ACTIVE' AND deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_permissions_user_endpoint ON user_permissions(user_id, endpoint) WHERE status = 'ACTIVE' AND deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_permissions_valid_until ON user_permissions(valid_until) WHERE valid_until IS NOT NULL AND status = 'ACTIVE' AND deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_permissions_lookup ON user_permissions(user_id, endpoint, operation, scope) WHERE status = 'ACTIVE' AND deleted = FALSE;

-- Trigger (IF NOT EXISTS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_user_permissions_updated_at') THEN
        CREATE TRIGGER update_user_permissions_updated_at
            BEFORE UPDATE ON user_permissions
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

