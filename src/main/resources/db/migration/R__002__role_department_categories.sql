-- ============================================================================
-- R002: Seed Roles and Department Categories (Repeatable)
-- ============================================================================
-- Seeds system-wide roles and department categories for SYSTEM_TENANT_ID
-- These are reference data shared by all tenants
-- 
-- Repeatable Migration: Runs every time Flyway checks migrations
-- If content changes, Flyway will re-execute this script
-- 
-- Last Updated: 2025-11-13
-- ============================================================================

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
    ON CONFLICT (uid) DO UPDATE SET 
        category_name = EXCLUDED.category_name,
        description = EXCLUDED.description,
        display_order = EXCLUDED.display_order
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
    ON CONFLICT (uid) DO UPDATE SET 
        category_name = EXCLUDED.category_name,
        description = EXCLUDED.description,
        display_order = EXCLUDED.display_order
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
    ON CONFLICT (uid) DO UPDATE SET 
        category_name = EXCLUDED.category_name,
        description = EXCLUDED.description,
        display_order = EXCLUDED.display_order
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
    ON CONFLICT (uid) DO UPDATE SET 
        category_name = EXCLUDED.category_name,
        description = EXCLUDED.description,
        display_order = EXCLUDED.display_order
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
    ON CONFLICT (uid) DO UPDATE SET 
        category_name = EXCLUDED.category_name,
        description = EXCLUDED.description,
        display_order = EXCLUDED.display_order
    RETURNING id INTO support_cat_id;
    
    IF support_cat_id IS NULL THEN
        SELECT id INTO support_cat_id FROM common_company.common_department_category 
        WHERE uid = 'SYS-CAT-005';
    END IF;

    -- ========================================================================
    -- SEED: Roles
    -- ========================================================================
    -- ✅ Platform-level system roles that are shared by ALL tenants
    -- These roles are NOT copied to tenants - all tenants use the same platform roles
    -- Tenant-specific roles can be created but are not shown in standard role lists
    INSERT INTO common_user.common_role 
        (tenant_id, uid, role_name, role_code, description, department_category_id, is_system_role, display_order, is_active)
    VALUES
        -- Production Roles
        (system_tenant_id, 'SYS-ROLE-001', 'Production Manager', 'PROD_MANAGER', 'Üretim yöneticisi', prod_cat_id, TRUE, 1, TRUE),
        (system_tenant_id, 'SYS-ROLE-002', 'Production Worker', 'PROD_WORKER', 'Üretim işçisi', prod_cat_id, TRUE, 2, TRUE),
        (system_tenant_id, 'SYS-ROLE-003', 'Quality Control', 'QC', 'Kalite kontrol', prod_cat_id, TRUE, 3, TRUE),
        -- Administration Roles
        (system_tenant_id, 'SYS-ROLE-004', 'Administrator', 'ADMIN', 'Sistem yöneticisi', admin_cat_id, TRUE, 4, TRUE),
        (system_tenant_id, 'SYS-ROLE-005', 'HR Manager', 'HR_MANAGER', 'İnsan kaynakları yöneticisi', admin_cat_id, TRUE, 5, TRUE),
        -- Logistics Roles
        (system_tenant_id, 'SYS-ROLE-006', 'Logistics Manager', 'LOG_MANAGER', 'Lojistik yöneticisi', logistics_cat_id, TRUE, 6, TRUE),
        (system_tenant_id, 'SYS-ROLE-007', 'Warehouse Worker', 'WAREHOUSE_WORKER', 'Depo işçisi', logistics_cat_id, TRUE, 7, TRUE),
        -- Platform Admin Role (special - system-wide access)
        (system_tenant_id, 'SYS-ROLE-0001', 'Platform Administrator', 'PLATFORM_ADMIN', 'Full platform access - can create tenants, manage system settings, access all tenant data', NULL, TRUE, 0, TRUE)
    ON CONFLICT (uid) DO UPDATE SET
        role_name = EXCLUDED.role_name,
        role_code = EXCLUDED.role_code,
        description = EXCLUDED.description,
        department_category_id = EXCLUDED.department_category_id,
        display_order = EXCLUDED.display_order;

END $$;

