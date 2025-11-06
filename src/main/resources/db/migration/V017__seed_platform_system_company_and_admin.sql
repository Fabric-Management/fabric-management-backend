-- ============================================================================
-- V17: Seed Data - Platform System Company and Platform Admin User
-- ============================================================================
-- Creates Platform System Company (special company for platform admin users)
-- Seeds initial platform admin user for platform management
-- 
-- SECURITY: company_id remains NOT NULL for all users, including platform admin
-- Platform admin belongs to special "Platform System" company
-- 
-- IMPORTANT: Change email and password hash before running!
-- Last Updated: 2025-01-27
-- ============================================================================

DO $$
DECLARE
    system_tenant_id UUID := '00000000-0000-0000-0000-000000000000'::UUID;
    
    -- ⚠️ CHANGE THESE VALUES BEFORE RUNNING
    platform_admin_email VARCHAR := 'akkaya64@hotmail.com';
    platform_admin_password_hash VARCHAR := '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';  -- Default: "admin123"
    platform_admin_first_name VARCHAR := 'Platform';
    platform_admin_last_name VARCHAR := 'Admin';
    
    -- Generated IDs
    platform_company_id UUID;
    user_id UUID;
    contact_id UUID;
    platform_role_id UUID;  -- Renamed to avoid ambiguity with column name
    auth_user_id UUID;
    
    -- Check flags
    company_exists BOOLEAN := FALSE;
    user_exists BOOLEAN := FALSE;
    
    -- Department seeding variables
    prod_cat_id UUID;
    admin_cat_id UUID;
    logistics_cat_id UUID;
    util_cat_id UUID;
    support_cat_id UUID;
    
    -- Department IDs (for platform-level seeding)
    fiber_dept_id UUID;
    yarn_dept_id UUID;
    weaving_dept_id UUID;
    dyeing_dept_id UUID;
    quality_dept_id UUID;
    hr_dept_id UUID;
    finance_dept_id UUID;
    admin_office_dept_id UUID;
    mgmt_dept_id UUID;
    warehouse_dept_id UUID;
    procurement_dept_id UUID;
    shipping_dept_id UUID;
    maintenance_dept_id UUID;
    energy_dept_id UUID;
    kitchen_dept_id UUID;
    it_dept_id UUID;
    security_dept_id UUID;
    cleaning_dept_id UUID;
    
    -- Role IDs (for platform-level seeding)
    prod_mgr_role_id UUID;
    prod_worker_role_id UUID;
    qc_role_id UUID;
    admin_role_id UUID;
    hr_mgr_role_id UUID;
    log_mgr_role_id UUID;
    warehouse_worker_role_id UUID;
BEGIN
    -- ========================================================================
    -- STEP 1: Create Platform System Company (if not exists)
    -- ========================================================================
    -- Check if Platform System Company already exists
    -- For root companies: tenant_id = company_id
    SELECT id INTO platform_company_id
    FROM common_company.common_company
    WHERE company_name = 'Platform System'
    AND tax_id = 'PLATFORM-SYSTEM-001'
    LIMIT 1;

    IF platform_company_id IS NOT NULL THEN
        RAISE NOTICE 'Platform System Company already exists: %', platform_company_id;
        company_exists := TRUE;
    ELSE
        -- Create Platform System Company
        platform_company_id := gen_random_uuid();
        
        INSERT INTO common_company.common_company (
            id, tenant_id, uid,
            company_name, tax_id, company_type,
            parent_company_id,  -- NULL (root company)
            is_active,
            created_at, updated_at, version
        ) VALUES (
            platform_company_id,
            platform_company_id,  -- tenant_id = company_id for root companies (will be set correctly)
            'SYS-PLATFORM-001',
            'Platform System',
            'PLATFORM-SYSTEM-001',
            'IT_SERVICE_PROVIDER',  -- Using IT_SERVICE_PROVIDER as closest match (platform is IT service)
            NULL,  -- No parent
            TRUE,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
        
        -- tenant_id already set to platform_company_id in INSERT above
        
        RAISE NOTICE '✅ Platform System Company created: %', platform_company_id;
    END IF;

    -- ========================================================================
    -- STEP 2: Check if platform admin already exists
    -- ========================================================================
    SELECT EXISTS(
        SELECT 1 FROM common_user.common_user u
        JOIN common_communication.common_user_contact uc ON u.id = uc.user_id
        JOIN common_communication.common_contact c ON uc.contact_id = c.id
        WHERE u.tenant_id = system_tenant_id
        AND c.contact_value = platform_admin_email
        AND c.is_verified = TRUE
    ) INTO user_exists;

    IF user_exists THEN
        RAISE NOTICE 'Platform admin already exists (%), skipping user creation...', platform_admin_email;
        RETURN;
    END IF;

    RAISE NOTICE 'Creating platform admin user: %', platform_admin_email;

    -- ========================================================================
    -- STEP 3: Generate UUIDs
    -- ========================================================================
    user_id := gen_random_uuid();
    contact_id := gen_random_uuid();
    auth_user_id := gen_random_uuid();

    -- ========================================================================
    -- STEP 4: Create User (belongs to Platform System Company)
    -- ========================================================================
    INSERT INTO common_user.common_user (
        id, tenant_id, uid,
        first_name, last_name, display_name,
        company_id,  -- ✅ Platform admin belongs to Platform System Company
        is_active,
        created_at, updated_at, version
    ) VALUES (
        user_id,
        system_tenant_id,
        'SYS-USER-0001',
        platform_admin_first_name,
        platform_admin_last_name,
        platform_admin_first_name || ' ' || platform_admin_last_name,
        platform_company_id,  -- ✅ NOT NULL - belongs to Platform System Company
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    RAISE NOTICE '✅ User created: %', user_id;

    -- ========================================================================
    -- STEP 5: Create Contact (Email)
    -- ========================================================================
    INSERT INTO common_communication.common_contact (
        id, tenant_id, uid,
        contact_value, contact_type,
        is_verified, is_primary, is_personal,
        label,
        is_active,
        created_at, updated_at, version
    ) VALUES (
        contact_id,
        system_tenant_id,
        'SYS-CONTACT-0001',
        platform_admin_email,
        'EMAIL',
        TRUE,  -- Pre-verified for platform admin
        TRUE,
        TRUE,  -- Personal contact
        'Primary Email',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    RAISE NOTICE '✅ Contact created: %', contact_id;

    -- ========================================================================
    -- STEP 6: Link User and Contact
    -- ========================================================================
    INSERT INTO common_communication.common_user_contact (
        tenant_id, uid,
        user_id, contact_id,
        is_default, is_for_authentication,
        is_active,
        created_at, updated_at, version
    ) VALUES (
        system_tenant_id,
        'SYS-USER-CONTACT-0001',
        user_id,
        contact_id,
        TRUE,  -- Default contact
        TRUE,  -- For authentication
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    RAISE NOTICE '✅ User-Contact link created';

    -- ========================================================================
    -- STEP 7: Get or Create PLATFORM_ADMIN Role
    -- ========================================================================
    SELECT id INTO platform_role_id
    FROM common_company.common_role
    WHERE tenant_id = system_tenant_id
    AND role_code = 'PLATFORM_ADMIN'
    AND is_active = TRUE
    LIMIT 1;

    IF platform_role_id IS NULL THEN
        RAISE NOTICE 'PLATFORM_ADMIN role not found, creating...';
        platform_role_id := gen_random_uuid();
        
        INSERT INTO common_company.common_role (
            id, tenant_id, uid,
            role_name, role_code, description,
            is_active,
            created_at, updated_at, version
        ) VALUES (
            platform_role_id,
            system_tenant_id,
            'SYS-ROLE-0001',
            'Platform Administrator',
            'PLATFORM_ADMIN',
            'Full platform access - can create tenants, manage system settings, access all tenant data',
            TRUE,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
        
        RAISE NOTICE '✅ PLATFORM_ADMIN role created: %', platform_role_id;
    ELSE
        RAISE NOTICE '✅ Using existing PLATFORM_ADMIN role: %', platform_role_id;
    END IF;

    -- ========================================================================
    -- STEP 8: Assign Role to User
    -- ========================================================================
    UPDATE common_user.common_user
    SET role_id = platform_role_id
    WHERE id = user_id;

    RAISE NOTICE '✅ Role assigned to user';

    -- ========================================================================
    -- STEP 9: Create AuthUser (for password authentication)
    -- ========================================================================
    INSERT INTO common_auth.common_auth_user (
        id, tenant_id, uid,
        contact_id,
        password_hash,
        is_verified, is_active,
        created_at, updated_at, version
    ) VALUES (
        auth_user_id,
        system_tenant_id,
        'SYS-AUTH-0001',
        contact_id,
        platform_admin_password_hash,  -- ⚠️ Pre-hashed BCrypt password
        TRUE,  -- Pre-verified
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    RAISE NOTICE '✅ AuthUser created: %', auth_user_id;

    -- ========================================================================
    -- STEP 10: Seed Platform-Level Departments and Positions
    -- ========================================================================
    -- Platform-level departments and positions serve as reference catalog
    -- These are copied to new tenants during tenant seeding
    RAISE NOTICE 'Seeding platform-level departments and positions...';
    
    -- Get category IDs
    BEGIN
        -- Get category IDs
        SELECT id INTO prod_cat_id FROM common_company.common_department_category 
            WHERE tenant_id = system_tenant_id AND uid = 'SYS-CAT-001';
        SELECT id INTO admin_cat_id FROM common_company.common_department_category 
            WHERE tenant_id = system_tenant_id AND uid = 'SYS-CAT-002';
        SELECT id INTO logistics_cat_id FROM common_company.common_department_category 
            WHERE tenant_id = system_tenant_id AND uid = 'SYS-CAT-004';
        SELECT id INTO util_cat_id FROM common_company.common_department_category 
            WHERE tenant_id = system_tenant_id AND uid = 'SYS-CAT-003';
        SELECT id INTO support_cat_id FROM common_company.common_department_category 
            WHERE tenant_id = system_tenant_id AND uid = 'SYS-CAT-005';
        
        -- Get role IDs
        SELECT id INTO prod_mgr_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'PROD_MANAGER';
        SELECT id INTO prod_worker_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'PROD_WORKER';
        SELECT id INTO qc_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'QC';
        SELECT id INTO admin_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'ADMIN';
        SELECT id INTO hr_mgr_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'HR_MANAGER';
        SELECT id INTO log_mgr_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'LOG_MANAGER';
        SELECT id INTO warehouse_worker_role_id FROM common_company.common_role 
            WHERE tenant_id = system_tenant_id AND role_code = 'WAREHOUSE_WORKER';
        
        -- ====================================================================
        -- PRODUCTION DEPARTMENTS
        -- ====================================================================
        -- Fiber & Raw Material
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-001', platform_company_id, 
             'Fiber & Raw Material', 'FIBERRAWMATERIAL', 
             'Fiber procurement and raw material management', prod_cat_id, TRUE, 1, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO fiber_dept_id;
        
        IF fiber_dept_id IS NULL THEN
            SELECT id INTO fiber_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-001';
        END IF;
        
        -- Yarn Production
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-002', platform_company_id, 
             'Yarn Production', 'YARNPRODUCTION', 
             'Yarn manufacturing operations', prod_cat_id, TRUE, 2, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO yarn_dept_id;
        
        IF yarn_dept_id IS NULL THEN
            SELECT id INTO yarn_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-002';
        END IF;
        
        -- Weaving & Knitting
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-003', platform_company_id, 
             'Weaving & Knitting', 'WEAVINGKNITTING', 
             'Fabric weaving and knitting operations', prod_cat_id, TRUE, 3, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO weaving_dept_id;
        
        IF weaving_dept_id IS NULL THEN
            SELECT id INTO weaving_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-003';
        END IF;
        
        -- Dyeing & Finishing
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-004', platform_company_id, 
             'Dyeing & Finishing', 'DYEINGFINISHING', 
             'Fabric dyeing and finishing operations', prod_cat_id, TRUE, 4, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO dyeing_dept_id;
        
        IF dyeing_dept_id IS NULL THEN
            SELECT id INTO dyeing_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-004';
        END IF;
        
        -- Quality Control
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-005', platform_company_id, 
             'Quality Control', 'QUALITYCONTROL', 
             'Quality assurance and laboratory testing', prod_cat_id, TRUE, 5, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO quality_dept_id;
        
        IF quality_dept_id IS NULL THEN
            SELECT id INTO quality_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-005';
        END IF;
        
        -- ====================================================================
        -- ADMINISTRATION DEPARTMENTS
        -- ====================================================================
        -- Human Resources
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-006', platform_company_id, 
             'Human Resources', 'HUMANRESOURCES', 
             'Human resources management', admin_cat_id, TRUE, 6, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO hr_dept_id;
        
        IF hr_dept_id IS NULL THEN
            SELECT id INTO hr_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-006';
        END IF;
        
        -- Finance & Accounting
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-007', platform_company_id, 
             'Finance & Accounting', 'FINANCEACCOUNTING', 
             'Financial management and accounting', admin_cat_id, TRUE, 7, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO finance_dept_id;
        
        IF finance_dept_id IS NULL THEN
            SELECT id INTO finance_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-007';
        END IF;
        
        -- Administration Office
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-008', platform_company_id, 
             'Administration Office', 'ADMINISTRATIONOFFICE', 
             'General administration and office management', admin_cat_id, TRUE, 8, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO admin_office_dept_id;
        
        IF admin_office_dept_id IS NULL THEN
            SELECT id INTO admin_office_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-008';
        END IF;
        
        -- Management & Planning
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-009', platform_company_id, 
             'Management & Planning', 'MANAGEMENTPLANNING', 
             'Executive management and strategic planning', admin_cat_id, TRUE, 9, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO mgmt_dept_id;
        
        IF mgmt_dept_id IS NULL THEN
            SELECT id INTO mgmt_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-009';
        END IF;
        
        -- ====================================================================
        -- LOGISTICS DEPARTMENTS
        -- ====================================================================
        -- Warehouse
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-010', platform_company_id, 
             'Warehouse', 'WAREHOUSE', 
             'Warehouse management and storage', logistics_cat_id, TRUE, 10, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO warehouse_dept_id;
        
        IF warehouse_dept_id IS NULL THEN
            SELECT id INTO warehouse_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-010';
        END IF;
        
        -- Procurement & Supply
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-011', platform_company_id, 
             'Procurement & Supply', 'PROCUREMENTSUPPLY', 
             'Procurement and supply chain management', logistics_cat_id, TRUE, 11, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO procurement_dept_id;
        
        IF procurement_dept_id IS NULL THEN
            SELECT id INTO procurement_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-011';
        END IF;
        
        -- Shipping & Transport
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-012', platform_company_id, 
             'Shipping & Transport', 'SHIPPINGTRANSPORT', 
             'Shipping and transportation management', logistics_cat_id, TRUE, 12, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO shipping_dept_id;
        
        IF shipping_dept_id IS NULL THEN
            SELECT id INTO shipping_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-012';
        END IF;
        
        -- ====================================================================
        -- UTILITY DEPARTMENTS
        -- ====================================================================
        -- Maintenance
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-013', platform_company_id, 
             'Maintenance', 'MAINTENANCE', 
             'Equipment maintenance and repair', util_cat_id, TRUE, 13, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO maintenance_dept_id;
        
        IF maintenance_dept_id IS NULL THEN
            SELECT id INTO maintenance_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-013';
        END IF;
        
        -- Energy & Facilities
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-014', platform_company_id, 
             'Energy & Facilities', 'ENERGYFACILITIES', 
             'Energy generation and facility operations', util_cat_id, TRUE, 14, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO energy_dept_id;
        
        IF energy_dept_id IS NULL THEN
            SELECT id INTO energy_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-014';
        END IF;
        
        -- Kitchen & Catering
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-015', platform_company_id, 
             'Kitchen & Catering', 'KITCHENCATERING', 
             'Kitchen and cafeteria services', util_cat_id, TRUE, 15, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO kitchen_dept_id;
        
        IF kitchen_dept_id IS NULL THEN
            SELECT id INTO kitchen_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-015';
        END IF;
        
        -- ====================================================================
        -- SUPPORT DEPARTMENTS
        -- ====================================================================
        -- IT Services
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-016', platform_company_id, 
             'IT Services', 'ITSERVICES', 
             'IT support and system administration', support_cat_id, TRUE, 16, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO it_dept_id;
        
        IF it_dept_id IS NULL THEN
            SELECT id INTO it_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-016';
        END IF;
        
        -- Security
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-017', platform_company_id, 
             'Security', 'SECURITY', 
             'Security and access control', support_cat_id, TRUE, 17, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO security_dept_id;
        
        IF security_dept_id IS NULL THEN
            SELECT id INTO security_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-017';
        END IF;
        
        -- Cleaning Services
        INSERT INTO common_company.common_department 
            (id, tenant_id, uid, company_id, department_name, department_code, description, 
             department_category_id, is_system_department, display_order, is_active)
        VALUES 
            (gen_random_uuid(), system_tenant_id, 'SYS-DEPT-018', platform_company_id, 
             'Cleaning Services', 'CLEANINGSERVICES', 
             'Cleaning and janitorial services', support_cat_id, TRUE, 18, TRUE)
        ON CONFLICT (uid) DO UPDATE SET department_name = EXCLUDED.department_name
        RETURNING id INTO cleaning_dept_id;
        
        IF cleaning_dept_id IS NULL THEN
            SELECT id INTO cleaning_dept_id FROM common_company.common_department WHERE uid = 'SYS-DEPT-018';
        END IF;
        
        RAISE NOTICE '✅ Platform-level departments seeded';
        
        -- NOTE: Position seeds will be added via TenantSeedService
        -- Positions are too numerous to seed in migration files
        -- They will be created during tenant seeding process
        
    EXCEPTION
        WHEN OTHERS THEN
            RAISE NOTICE 'Warning: Failed to seed platform-level departments: %', SQLERRM;
    END;

    RAISE NOTICE '';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE '✅ Platform System Company and Admin User Created Successfully!';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Platform Company ID: %', platform_company_id;
    RAISE NOTICE 'Platform Company Name: Platform System';
    RAISE NOTICE '';
    RAISE NOTICE 'Admin Email: %', platform_admin_email;
    RAISE NOTICE 'Default Password: admin123 (CHANGE IN PRODUCTION!)';
    RAISE NOTICE 'User ID: %', user_id;
    RAISE NOTICE 'Contact ID: %', contact_id;
    RAISE NOTICE 'Role: PLATFORM_ADMIN';
    RAISE NOTICE '';
    RAISE NOTICE '⚠️ IMPORTANT: Change password after first login!';
    RAISE NOTICE '⚠️ IMPORTANT: Update email in production!';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';

EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Failed to create platform system company/admin: %', SQLERRM;
END $$;

COMMENT ON TABLE common_user.common_user IS 'All users, including platform admin, must belong to a company. Platform admin belongs to Platform System Company.';
COMMENT ON COLUMN common_user.common_user.company_id IS 'Company ID - REQUIRED for all users. Platform admin belongs to Platform System Company.';

