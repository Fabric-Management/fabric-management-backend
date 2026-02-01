-- ============================================================================
-- V37: Move Role table from common_company to common_user schema
-- ============================================================================
-- Role is a user-level concept; Department/Position remain in company module.
-- DB empty per plan — create in common_user, drop from common_company.
-- User.role_id FK updated to reference common_user.common_role.
-- ============================================================================

-- ============================================================================
-- 1. Create common_role in common_user (same structure as common_company)
-- ============================================================================
CREATE TABLE common_user.common_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,

    role_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    department_category_id UUID REFERENCES common_company.common_department_category(id),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_role_tenant_code UNIQUE (tenant_id, role_code)
);

CREATE INDEX idx_common_user_role_tenant ON common_user.common_role(tenant_id);
CREATE INDEX idx_common_user_role_category ON common_user.common_role(department_category_id);
CREATE INDEX idx_common_user_role_code ON common_user.common_role(role_code);
CREATE INDEX idx_common_user_role_active ON common_user.common_role(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_user.common_role IS 'User roles (e.g. Admin, Manager). Moved from common_company for domain alignment.';

-- ============================================================================
-- 2. Copy data from common_company.common_role (if any)
-- ============================================================================
INSERT INTO common_user.common_role (
    id, tenant_id, uid, role_name, role_code, description,
    department_category_id, is_system_role, display_order,
    is_active, created_at, created_by, updated_at, updated_by, version
)
SELECT id, tenant_id, uid, role_name, role_code, description,
       department_category_id, is_system_role, display_order,
       is_active, created_at, created_by, updated_at, updated_by, version
FROM common_company.common_role;

-- ============================================================================
-- 3. Drop FK on common_user.common_user, point to common_user.common_role
-- ============================================================================
ALTER TABLE common_user.common_user DROP CONSTRAINT IF EXISTS fk_user_role;

ALTER TABLE common_user.common_user
    ADD CONSTRAINT fk_user_role
    FOREIGN KEY (role_id) REFERENCES common_user.common_role(id);

COMMENT ON COLUMN common_user.common_user.role_id IS 'User role — references common_user.common_role';

-- ============================================================================
-- 4. Update common_position.default_role_id FK to common_user.common_role
-- ============================================================================
ALTER TABLE common_company.common_position DROP CONSTRAINT IF EXISTS fk_position_role;

ALTER TABLE common_company.common_position
    ADD CONSTRAINT fk_position_role
    FOREIGN KEY (default_role_id) REFERENCES common_user.common_role(id) ON DELETE SET NULL;

-- ============================================================================
-- 5. Drop common_company.common_role
-- ============================================================================
DROP TABLE common_company.common_role;
