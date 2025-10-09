-- =====================================================================================
-- POLICY REGISTRY - SEED DATA
-- =====================================================================================
-- Populates policy_registry table with platform-wide authorization rules.
-- 
-- Design principles:
-- - Secure by default (most endpoints require authentication/authorization)
-- - Explicit rules for high-risk operations (DELETE, sensitive data)
-- - Company type restrictions where applicable
-- - Grant requirements for cross-company operations
-- 
-- Categories:
-- 1. User Management (Admin only)
-- 2. Company Management (Role-based + Company type guardrails)
-- 3. Contact Management (Company-scoped)
-- 4. Cross-Company Operations (Grant required)
-- =====================================================================================

-- =====================================================================================
-- USER MANAGEMENT POLICIES
-- =====================================================================================

-- Create User (Admin/Super Admin only, INTERNAL companies preferred)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'user_create_admin_only',
    '/api/v1/users',
    'WRITE',
    'ADMIN',
    ARRAY['INTERNAL', 'CUSTOMER']::TEXT[],
    false,
    'COMPANY',
    true,
    1,
    '{"description": "Only admins can create users", "risk_level": "high"}',
    NOW(),
    NOW()
);

-- Delete User (Super Admin only, high risk)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'user_delete_super_admin_only',
    '/api/v1/users/{userId}',
    'DELETE',
    'SUPER_ADMIN',
    ARRAY['INTERNAL']::TEXT[],
    true,
    'GLOBAL',
    true,
    1,
    '{"description": "User deletion requires super admin + explicit grant", "risk_level": "critical"}',
    NOW(),
    NOW()
);

-- =====================================================================================
-- COMPANY MANAGEMENT POLICIES
-- =====================================================================================

-- Create Company (Admin only, INTERNAL companies)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'company_create_internal_only',
    '/api/v1/companies',
    'WRITE',
    'ADMIN',
    ARRAY['INTERNAL']::TEXT[],
    false,
    'GLOBAL',
    true,
    1,
    '{"description": "Only INTERNAL companies can create new companies", "risk_level": "medium"}',
    NOW(),
    NOW()
);

-- Delete Company (Super Admin only, critical operation)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'company_delete_critical',
    '/api/v1/companies/{companyId}',
    'DELETE',
    'SUPER_ADMIN',
    ARRAY['INTERNAL']::TEXT[],
    true,
    'GLOBAL',
    true,
    1,
    '{"description": "Company deletion requires super admin + grant", "risk_level": "critical"}',
    NOW(),
    NOW()
);

-- Cross-Company Access (Grant required for CUSTOMER/SUPPLIER)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'cross_company_access_restricted',
    '/api/v1/companies/{companyId}/users',
    'READ',
    'USER',
    ARRAY['INTERNAL', 'CUSTOMER', 'SUPPLIER']::TEXT[],
    true,
    'CROSS_COMPANY',
    true,
    1,
    '{"description": "Cross-company user access requires grant (except INTERNAL)", "risk_level": "medium"}',
    NOW(),
    NOW()
);

-- =====================================================================================
-- CONTACT MANAGEMENT POLICIES
-- =====================================================================================

-- Delete Contact (Manager+ only)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'contact_delete_manager_only',
    '/api/v1/contacts/{contactId}',
    'DELETE',
    'MANAGER',
    NULL,
    false,
    'COMPANY',
    true,
    1,
    '{"description": "Contact deletion requires Manager or higher", "risk_level": "medium"}',
    NOW(),
    NOW()
);

-- =====================================================================================
-- SYSTEM-WIDE POLICIES
-- =====================================================================================

-- Bulk Operations (Admin only, grant required)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'bulk_operations_restricted',
    '/api/v1/%/bulk/%',
    'WRITE',
    'ADMIN',
    ARRAY['INTERNAL']::TEXT[],
    true,
    'GLOBAL',
    true,
    1,
    '{"description": "Bulk operations require admin + grant (pattern match)", "risk_level": "high"}',
    NOW(),
    NOW()
);

-- Export Operations (Manager+, audit logged)
INSERT INTO policy_registry (
    id, policy_name, endpoint, operation, required_role, allowed_company_types,
    requires_grant, data_scope, is_active, version, metadata, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'export_manager_only',
    '/api/v1/%/export',
    'READ',
    'MANAGER',
    NULL,
    false,
    'COMPANY',
    true,
    1,
    '{"description": "Data exports require Manager or higher", "risk_level": "medium"}',
    NOW(),
    NOW()
);

-- =====================================================================================
-- COMMENTS
-- =====================================================================================

-- Notes:
-- 1. Endpoint patterns use '%' for wildcard matching (SQL LIKE pattern)
-- 2. allowed_company_types NULL means "all types allowed"
-- 3. requires_grant=true forces explicit user permission (Advanced Settings)
-- 4. Policies are version-controlled (version field) for audit trail
-- 5. metadata (JSONB) stores additional context (description, risk level, etc.)
-- 
-- Pattern examples:
-- - '/api/v1/users/{userId}' - specific resource pattern
-- - '/api/v1/%/bulk/%' - wildcard for all bulk operations
-- - '/api/v1/%/export' - wildcard for all export endpoints

