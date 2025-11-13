-- ============================================================================
-- V13: Role & Department Architecture - Dynamic Management Support
-- ============================================================================
-- Establishes fully dynamic, database-driven Role and Department management
-- Replaces static string-based fields with relational entities
-- Includes seed data for initial roles, categories, and departments
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

CREATE INDEX IF NOT EXISTS idx_department_category_tenant ON common_company.common_department_category(tenant_id);
CREATE INDEX IF NOT EXISTS idx_department_category_active ON common_company.common_department_category(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_department_category IS 'Department categories for organizing departments (Production, Administration, etc.)';

-- ============================================================================
-- TABLE: common_role
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_role (
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

CREATE INDEX IF NOT EXISTS idx_role_tenant ON common_company.common_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_role_category ON common_company.common_role(department_category_id);
CREATE INDEX IF NOT EXISTS idx_role_code ON common_company.common_role(role_code);
CREATE INDEX IF NOT EXISTS idx_role_active ON common_company.common_role(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_role IS 'Roles within the organization (e.g., Production Manager, Quality Control, etc.)';
COMMENT ON COLUMN common_company.common_role.is_system_role IS 'System roles cannot be deleted or modified by users';

-- ============================================================================
-- TABLE: common_department
-- ============================================================================
-- Drop existing table from V002 to recreate with new structure
DROP TABLE IF EXISTS common_company.common_department CASCADE;

CREATE TABLE common_company.common_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    company_id UUID NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    manager_id UUID,
    department_category_id UUID REFERENCES common_company.common_department_category(id),
    parent_department_id UUID REFERENCES common_company.common_department(id),
    is_system_department BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_department_company FOREIGN KEY (company_id) 
        REFERENCES common_company.common_company(id) ON DELETE CASCADE,
    CONSTRAINT uq_department_tenant_code UNIQUE (tenant_id, department_code)
);

CREATE INDEX IF NOT EXISTS idx_department_tenant ON common_company.common_department(tenant_id);
CREATE INDEX IF NOT EXISTS idx_department_company ON common_company.common_department(company_id);
CREATE INDEX IF NOT EXISTS idx_department_category ON common_company.common_department(department_category_id);
CREATE INDEX IF NOT EXISTS idx_department_parent ON common_company.common_department(parent_department_id);
CREATE INDEX IF NOT EXISTS idx_department_code ON common_company.common_department(department_code);
CREATE INDEX IF NOT EXISTS idx_department_active ON common_company.common_department(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_department IS 'Organizational departments (e.g., Production, Quality Control, etc.)';
COMMENT ON COLUMN common_company.common_department.is_system_department IS 'System departments cannot be deleted or modified by users';

-- ============================================================================
-- ALTER: Add foreign key constraint for role_id (column already exists in V003)
-- ============================================================================
ALTER TABLE common_user.common_user
ADD CONSTRAINT IF NOT EXISTS fk_user_role 
    FOREIGN KEY (role_id) 
    REFERENCES common_company.common_role(id);

-- ============================================================================
-- TABLE: common_user_department (Junction table)
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
    CONSTRAINT fk_user_dept_department FOREIGN KEY (department_id) 
        REFERENCES common_company.common_department(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_dept_user ON common_user.common_user_department(user_id);
CREATE INDEX IF NOT EXISTS idx_user_dept_dept ON common_user.common_user_department(department_id);
CREATE INDEX IF NOT EXISTS idx_user_dept_primary ON common_user.common_user_department(user_id, is_primary) WHERE is_primary = TRUE;

COMMENT ON TABLE common_user.common_user_department IS 'Many-to-Many relationship between User and Department';
COMMENT ON COLUMN common_user.common_user_department.is_primary IS 'Primary department assignment (user can have multiple departments)';

-- ============================================================================
-- NOTE: Seed data moved to R__002__role_department_categories.sql (repeatable migration)
-- ============================================================================
