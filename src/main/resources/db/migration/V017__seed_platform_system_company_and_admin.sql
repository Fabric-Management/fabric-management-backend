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

