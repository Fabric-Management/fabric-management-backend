-- ============================================================================
-- V13: Role & Department Architecture - Dynamic Management Support
-- ============================================================================
-- Establishes fully dynamic, database-driven Role and Department management
-- Replaces static string-based fields with relational entities
-- Last Updated: 2025-01-27
-- ============================================================================

-- ============================================================================
-- TABLE: common_department_category
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_department_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    category_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER DEFAULT 0,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_department_category_tenant ON common_company.common_department_category(tenant_id);
CREATE INDEX idx_department_category_active ON common_company.common_department_category(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_department_category_display_order ON common_company.common_department_category(display_order);

COMMENT ON TABLE common_company.common_department_category IS 'Department categories for grouping departments (Production, Administrative, Utility, etc.)';
COMMENT ON COLUMN common_company.common_department_category.category_name IS 'Category name: Production, Administrative, Utility, Logistics & Warehouse, Support & Audit';
COMMENT ON COLUMN common_company.common_department_category.display_order IS 'Sort order for UI display';

-- ============================================================================
-- TABLE: common_role
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    role_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_role_tenant_code UNIQUE(tenant_id, role_code)
);

CREATE INDEX idx_role_tenant ON common_user.common_role(tenant_id);
CREATE INDEX idx_role_code ON common_user.common_role(role_code);
CREATE INDEX idx_role_active ON common_user.common_role(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_user.common_role IS 'Dynamic role management - database-driven roles (not enums)';
COMMENT ON COLUMN common_user.common_role.role_code IS 'Unique role code: ADMIN, DIRECTOR, MANAGER, SUPERVISOR, USER, INTERN, VIEWER';
COMMENT ON COLUMN common_user.common_role.role_name IS 'Human-readable role name';

-- ============================================================================
-- ALTER: common_department - Add category relationship
-- ============================================================================
ALTER TABLE common_company.common_department
ADD COLUMN IF NOT EXISTS department_category_id UUID;

ALTER TABLE common_company.common_department
ADD CONSTRAINT fk_department_category 
FOREIGN KEY (department_category_id) 
REFERENCES common_company.common_department_category(id) ON DELETE SET NULL;

CREATE INDEX idx_department_category ON common_company.common_department(department_category_id);

COMMENT ON COLUMN common_company.common_department.department_category_id IS 'Category classification (Production, Administrative, Utility, etc.)';

-- ============================================================================
-- TABLE: common_user_department (Many-to-Many junction)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_user_department (
    user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    
    PRIMARY KEY (user_id, department_id),
    
    CONSTRAINT fk_user_dept_user FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_dept_dept FOREIGN KEY (department_id) 
        REFERENCES common_company.common_department(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_dept_user ON common_user.common_user_department(user_id);
CREATE INDEX idx_user_dept_dept ON common_user.common_user_department(department_id);
CREATE INDEX idx_user_dept_primary ON common_user.common_user_department(user_id, is_primary) WHERE is_primary = TRUE;

COMMENT ON TABLE common_user.common_user_department IS 'Many-to-Many relationship between User and Department';
COMMENT ON COLUMN common_user.common_user_department.is_primary IS 'Primary department assignment (user can have multiple departments)';

-- ============================================================================
-- ALTER: common_user - Add role relationship
-- ============================================================================
ALTER TABLE common_user.common_user
ADD COLUMN IF NOT EXISTS role_id UUID;

ALTER TABLE common_user.common_user
ADD CONSTRAINT fk_user_role 
FOREIGN KEY (role_id) 
REFERENCES common_user.common_role(id) ON DELETE SET NULL;

CREATE INDEX idx_user_role ON common_user.common_user(role_id);

COMMENT ON COLUMN common_user.common_user.role_id IS 'User role assignment (One-to-Many: one role per user)';
COMMENT ON COLUMN common_user.common_user.department IS 'DEPRECATED: Kept temporarily for migration. Use common_user_department junction table instead.';

-- ============================================================================
-- Note: User.department String field is kept temporarily for data migration
-- It will be removed in a later migration after data is migrated to junction table
-- ============================================================================

