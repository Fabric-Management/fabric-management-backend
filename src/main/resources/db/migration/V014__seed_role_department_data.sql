-- ============================================================================
-- V14: Seed Data - Role, Department Category, and Department Initial Data
-- ============================================================================
-- Seeds initial roles, department categories, and departments
-- Uses SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000) for system-wide data
-- Last Updated: 2025-01-27
-- ============================================================================

-- System Tenant ID for seed data
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
    ON CONFLICT (uid) DO NOTHING
    RETURNING id INTO prod_cat_id;

    -- Administrative Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-002', 'Administrative', 
         'Ofis / yönetim / destek birimleri', 2, TRUE)
    ON CONFLICT (uid) DO NOTHING
    RETURNING id INTO admin_cat_id;

    -- Utility Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-003', 'Utility', 
         'Yardımcı hizmet birimleri', 3, TRUE)
    ON CONFLICT (uid) DO NOTHING
    RETURNING id INTO util_cat_id;

    -- Logistics & Warehouse Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-004', 'Logistics & Warehouse', 
         'Depo / sevkiyat / stok operasyonları', 4, TRUE)
    ON CONFLICT (uid) DO NOTHING
    RETURNING id INTO logistics_cat_id;

    -- Support & Audit Category
    INSERT INTO common_company.common_department_category 
        (id, tenant_id, uid, category_name, description, display_order, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-CAT-005', 'Support & Audit', 
         'Eğitim / dokümantasyon / denetim birimleri', 5, TRUE)
    ON CONFLICT (uid) DO NOTHING
    RETURNING id INTO support_cat_id;

    -- ========================================================================
    -- NOTE: Departments will be seeded per-tenant basis (not system-wide)
    -- Each tenant creates their own departments based on their needs
    -- Categories above are reference data that all tenants can use
    -- ========================================================================

    -- ========================================================================
    -- SEED: Roles (System-wide reference roles)
    -- ========================================================================
    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-001', 'Administrator', 'ADMIN', 
         'Full system access', TRUE)
    ON CONFLICT (uid) DO NOTHING;

    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-002', 'Director', 'DIRECTOR', 
         'Üst yönetim erişimi', TRUE)
    ON CONFLICT (uid) DO NOTHING;

    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-003', 'Manager', 'MANAGER', 
         'Departman yönetimi', TRUE)
    ON CONFLICT (uid) DO NOTHING;

    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-004', 'Supervisor', 'SUPERVISOR', 
         'Vardiya / ekip lideri', TRUE)
    ON CONFLICT (uid) DO NOTHING;

    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-005', 'User', 'USER', 
         'Standart çalışan', TRUE)
    ON CONFLICT (uid) DO NOTHING;

    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-006', 'Intern', 'INTERN', 
         'Stajyer erişimi', TRUE)
    ON CONFLICT (uid) DO NOTHING;

    INSERT INTO common_user.common_role 
        (id, tenant_id, uid, role_name, role_code, description, is_active)
    VALUES 
        (gen_random_uuid(), system_tenant_id, 'SYS-ROLE-007', 'Viewer', 'VIEWER', 
         'Sadece okuma yetkisi', TRUE)
    ON CONFLICT (uid) DO NOTHING;

END $$;

COMMENT ON TABLE common_company.common_department_category IS 'Seeded with: Production, Administrative, Utility, Logistics & Warehouse, Support & Audit';
COMMENT ON TABLE common_user.common_role IS 'Seeded with: ADMIN, DIRECTOR, MANAGER, SUPERVISOR, USER, INTERN, VIEWER';

