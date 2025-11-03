-- ============================================================================
-- V17: Seed Data - Platform Admin User
-- ============================================================================
-- Seeds initial platform admin user for platform management
-- This user can create tenants via sales-led onboarding
-- Uses SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000)
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
    user_id UUID;
    contact_id UUID;
    role_id UUID;
    auth_user_id UUID;
    
    -- Check flags
    user_exists BOOLEAN := FALSE;
    role_exists BOOLEAN := FALSE;
BEGIN
    -- Check if platform admin already exists
    SELECT EXISTS(
        SELECT 1 FROM common_user.common_user u
        JOIN common_communication.common_user_contact uc ON u.id = uc.user_id
        JOIN common_communication.common_contact c ON uc.contact_id = c.id
        WHERE u.tenant_id = system_tenant_id
        AND c.contact_value = platform_admin_email
        AND c.is_verified = TRUE
    ) INTO user_exists;

    IF user_exists THEN
        RAISE NOTICE 'Platform admin already exists (%), skipping creation...', platform_admin_email;
        RETURN;
    END IF;

    RAISE NOTICE 'Creating platform admin user: %', platform_admin_email;

    -- 1. Generate UUIDs
    user_id := gen_random_uuid();
    contact_id := gen_random_uuid();
    auth_user_id := gen_random_uuid();

    -- 2. Create User
    INSERT INTO common_user.common_user (
        id, tenant_id, uid,
        first_name, last_name, display_name,
        company_id,  -- NULL for platform admin (no company)
        is_active,
        created_at, updated_at, version
    ) VALUES (
        user_id,
        system_tenant_id,
        'SYS-USER-0001',
        platform_admin_first_name,
        platform_admin_last_name,
        platform_admin_first_name || ' ' || platform_admin_last_name,
        NULL,  -- Platform admin doesn't belong to any company
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    RAISE NOTICE '✅ User created: %', user_id;

    -- 3. Create Contact (Email)
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

    -- 4. Link User and Contact
    INSERT INTO common_communication.common_user_contact (
        user_id, contact_id, tenant_id,
        is_default, is_for_authentication,
        created_at, updated_at
    ) VALUES (
        user_id,
        contact_id,
        system_tenant_id,
        TRUE,  -- Default contact
        TRUE,  -- For authentication
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

    RAISE NOTICE '✅ User-Contact link created';

    -- 5. Get or Create PLATFORM_ADMIN Role
    SELECT id INTO role_id
    FROM common_user.common_role
    WHERE tenant_id = system_tenant_id
    AND role_code = 'PLATFORM_ADMIN'
    AND is_active = TRUE
    LIMIT 1;

    IF role_id IS NULL THEN
        RAISE NOTICE 'PLATFORM_ADMIN role not found, creating...';
        role_id := gen_random_uuid();
        
        INSERT INTO common_user.common_role (
            id, tenant_id, uid,
            role_name, role_code, description,
            is_active,
            created_at, updated_at, version
        ) VALUES (
            role_id,
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
        
        RAISE NOTICE '✅ PLATFORM_ADMIN role created: %', role_id;
    ELSE
        RAISE NOTICE '✅ Using existing PLATFORM_ADMIN role: %', role_id;
    END IF;

    -- 6. Assign Role to User
    UPDATE common_user.common_user
    SET role_id = role_id
    WHERE id = user_id;

    RAISE NOTICE '✅ Role assigned to user';

    -- 7. Create AuthUser (for password authentication)
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
    RAISE NOTICE '✅ Platform Admin User Created Successfully!';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Email: %', platform_admin_email;
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
        RAISE EXCEPTION 'Failed to create platform admin: %', SQLERRM;
END $$;

COMMENT ON TABLE common_user.common_user IS 'Includes platform admin users with SYSTEM_TENANT_ID for platform management';
COMMENT ON COLUMN common_user.common_user.tenant_id IS 'SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000) for platform admins, actual tenant_id for tenant users';

