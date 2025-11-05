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

CREATE INDEX idx_department_category_tenant ON common_company.common_department_category(tenant_id);
CREATE INDEX idx_department_category_active ON common_company.common_department_category(is_active) WHERE is_active = TRUE;

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

CREATE INDEX idx_role_tenant ON common_company.common_role(tenant_id);
CREATE INDEX idx_role_category ON common_company.common_role(department_category_id);
CREATE INDEX idx_role_code ON common_company.common_role(role_code);
CREATE INDEX idx_role_active ON common_company.common_role(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_role IS 'Roles within the organization (e.g., Production Manager, Quality Control, etc.)';
COMMENT ON COLUMN common_company.common_role.is_system_role IS 'System roles cannot be deleted or modified by users';

-- ============================================================================
-- TABLE: common_department
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    department_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
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
    
    CONSTRAINT uq_department_tenant_code UNIQUE (tenant_id, department_code)
);

CREATE INDEX idx_department_tenant ON common_company.common_department(tenant_id);
CREATE INDEX idx_department_category ON common_company.common_department(department_category_id);
CREATE INDEX idx_department_parent ON common_company.common_department(parent_department_id);
CREATE INDEX idx_department_code ON common_company.common_department(department_code);
CREATE INDEX idx_department_active ON common_company.common_department(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.common_department IS 'Organizational departments (e.g., Production, Quality Control, etc.)';
COMMENT ON COLUMN common_company.common_department.is_system_department IS 'System departments cannot be deleted or modified by users';

-- ============================================================================
-- SEED DATA: Roles, Department Categories, and Departments
-- ============================================================================
-- Uses SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000) for system-wide data

DO $$
DECLARE
    system_tenant_id UUID := '00000000-0000-0000-0000-000000000000'::UUID;
    prod_cat_id UUID;
    admin_cat_id UUID;
    util_cat_id UUID;
    logistics_cat_id UUID;
    support_cat_id UUID;
BEGIN
    -- ========================================================================
    -- SEED: Department Categories
    -- ========================================================================
    -- Production Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-001', 'Production', 
         'Üretim ile doğrudan ilgili departmanlar', 1, TRUE)
    ON CONFLICT (uid) DO UPDATE SET category_name = EXCLUDED.category_name
    RETURNING id INTO prod_cat_id;
    
    -- Get if already exists
    IF prod_cat_id IS NULL THEN
        SELECT id INTO prod_cat_id FROM common_company.common_department_category 
        WHERE uid = 'SYS-CAT-001';
    END IF;
    
    -- Administration Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-002', 'Administration', 
         'İdari ve yönetim departmanları', 2, TRUE)
    ON CONFLICT (uid) DO UPDATE SET category_name = EXCLUDED.category_name
    RETURNING id INTO admin_cat_id;
    
    IF admin_cat_id IS NULL THEN
        SELECT id INTO admin_cat_id FROM common_company.common_department_category 
        WHERE uid = 'SYS-CAT-002';
    END IF;
    
    -- Utility Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-003', 'Utility', 
         'Yardımcı hizmet departmanları', 3, TRUE)
    ON CONFLICT (uid) DO UPDATE SET category_name = EXCLUDED.category_name
    RETURNING id INTO util_cat_id;
    
    IF util_cat_id IS NULL THEN
        SELECT id INTO util_cat_id FROM common_company.common_department_category 
        WHERE uid = 'SYS-CAT-003';
    END IF;
    
    -- Logistics Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-004', 'Logistics', 
         'Lojistik ve tedarik departmanları', 4, TRUE)
    ON CONFLICT (uid) DO UPDATE SET category_name = EXCLUDED.category_name
    RETURNING id INTO logistics_cat_id;
    
    IF logistics_cat_id IS NULL THEN
        SELECT id INTO logistics_cat_id FROM common_company.common_department_category 
        WHERE uid = 'SYS-CAT-004';
    END IF;
    
    -- Support Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-005', 'Support', 
         'Destek ve hizmet departmanları', 5, TRUE)
    ON CONFLICT (uid) DO UPDATE SET category_name = EXCLUDED.category_name
    RETURNING id INTO support_cat_id;
    
    IF support_cat_id IS NULL THEN
        SELECT id INTO support_cat_id FROM common_company.common_department_category 
        WHERE uid = 'SYS-CAT-005';
    END IF;

    -- ========================================================================
    -- SEED: Roles
    -- ========================================================================
    INSERT INTO common_company.common_role 
        (tenant_id, uid, role_name, role_code, description, department_category_id, is_system_role, display_order, is_active)
    VALUES
        (system_tenant_id, 'SYS-ROLE-001', 'Production Manager', 'PROD_MANAGER', 'Üretim yöneticisi', prod_cat_id, TRUE, 1, TRUE),
        (system_tenant_id, 'SYS-ROLE-002', 'Production Worker', 'PROD_WORKER', 'Üretim işçisi', prod_cat_id, TRUE, 2, TRUE),
        (system_tenant_id, 'SYS-ROLE-003', 'Quality Control', 'QC', 'Kalite kontrol', prod_cat_id, TRUE, 3, TRUE),
        (system_tenant_id, 'SYS-ROLE-004', 'Administrator', 'ADMIN', 'Sistem yöneticisi', admin_cat_id, TRUE, 4, TRUE),
        (system_tenant_id, 'SYS-ROLE-005', 'HR Manager', 'HR_MANAGER', 'İnsan kaynakları yöneticisi', admin_cat_id, TRUE, 5, TRUE),
        (system_tenant_id, 'SYS-ROLE-006', 'Logistics Manager', 'LOG_MANAGER', 'Lojistik yöneticisi', logistics_cat_id, TRUE, 6, TRUE),
        (system_tenant_id, 'SYS-ROLE-007', 'Warehouse Worker', 'WAREHOUSE_WORKER', 'Depo işçisi', logistics_cat_id, TRUE, 7, TRUE)
    ON CONFLICT (uid) DO NOTHING;

    -- ========================================================================
    -- SEED: Departments
    -- ========================================================================
    INSERT INTO common_company.common_department 
        (tenant_id, uid, department_name, department_code, description, department_category_id, is_system_department, display_order, is_active)
    VALUES
        (system_tenant_id, 'SYS-DEPT-001', 'Production', 'PROD', 'Ana üretim departmanı', prod_cat_id, TRUE, 1, TRUE),
        (system_tenant_id, 'SYS-DEPT-002', 'Quality Control', 'QC', 'Kalite kontrol departmanı', prod_cat_id, TRUE, 2, TRUE),
        (system_tenant_id, 'SYS-DEPT-003', 'Maintenance', 'MAINT', 'Bakım departmanı', util_cat_id, TRUE, 3, TRUE),
        (system_tenant_id, 'SYS-DEPT-004', 'Administration', 'ADMIN', 'İdari departman', admin_cat_id, TRUE, 4, TRUE),
        (system_tenant_id, 'SYS-DEPT-005', 'Human Resources', 'HR', 'İnsan kaynakları', admin_cat_id, TRUE, 5, TRUE),
        (system_tenant_id, 'SYS-DEPT-006', 'Logistics', 'LOG', 'Lojistik departmanı', logistics_cat_id, TRUE, 6, TRUE),
        (system_tenant_id, 'SYS-DEPT-007', 'Warehouse', 'WAREHOUSE', 'Depo departmanı', logistics_cat_id, TRUE, 7, TRUE),
        (system_tenant_id, 'SYS-DEPT-008', 'IT Support', 'IT', 'IT destek departmanı', support_cat_id, TRUE, 8, TRUE)
    ON CONFLICT (uid) DO NOTHING;

END $$;
