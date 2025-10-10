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
-- 
-- Constants Reference (for maintainability):
-- - created_by/updated_by = 'SYSTEM' (PolicyConstants.CREATED_BY_SYSTEM)
-- - version = 0 (BaseEntity.VERSION_INITIAL)
-- - deleted = false (BaseEntity.DELETED_FALSE)
-- - policy_version = 'v1' (PolicyConstants.POLICY_VERSION_INITIAL)
-- =====================================================================================

-- =====================================================================================
-- USER MANAGEMENT POLICIES
-- =====================================================================================

-- Create User (Admin/Super Admin only, INTERNAL companies preferred)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/users',
    'WRITE',
    'COMPANY',
    ARRAY['ADMIN']::TEXT[],
    ARRAY['INTERNAL', 'CUSTOMER']::TEXT[],
    false,
    true,
    'v1',
    'Only admins can create users',
    '{"risk_level": "high"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- Delete User (Super Admin only, high risk)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/users/{userId}',
    'DELETE',
    'GLOBAL',
    ARRAY['SUPER_ADMIN']::TEXT[],
    ARRAY['INTERNAL']::TEXT[],
    true,
    true,
    'v1',
    'User deletion requires super admin + explicit grant',
    '{"risk_level": "critical"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- =====================================================================================
-- COMPANY MANAGEMENT POLICIES
-- =====================================================================================

-- Create Company (Admin only, INTERNAL companies)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/companies',
    'WRITE',
    'GLOBAL',
    ARRAY['ADMIN']::TEXT[],
    ARRAY['INTERNAL']::TEXT[],
    false,
    true,
    'v1',
    'Only INTERNAL companies can create new companies',
    '{"risk_level": "medium"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- Delete Company (Super Admin only, critical operation)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/companies/{companyId}',
    'DELETE',
    'GLOBAL',
    ARRAY['SUPER_ADMIN']::TEXT[],
    ARRAY['INTERNAL']::TEXT[],
    true,
    true,
    'v1',
    'Company deletion requires super admin + grant',
    '{"risk_level": "critical"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- Cross-Company Access (Grant required for CUSTOMER/SUPPLIER)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/companies/{companyId}/users',
    'READ',
    'CROSS_COMPANY',
    ARRAY['USER']::TEXT[],
    ARRAY['INTERNAL', 'CUSTOMER', 'SUPPLIER']::TEXT[],
    true,
    true,
    'v1',
    'Cross-company user access requires grant (except INTERNAL)',
    '{"risk_level": "medium"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- =====================================================================================
-- CONTACT MANAGEMENT POLICIES
-- =====================================================================================

-- Delete Contact (Manager+ only)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/contacts/{contactId}',
    'DELETE',
    'COMPANY',
    ARRAY['MANAGER', 'ADMIN']::TEXT[],
    NULL,
    false,
    true,
    'v1',
    'Contact deletion requires Manager or higher',
    '{"risk_level": "medium"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- =====================================================================================
-- SYSTEM-WIDE POLICIES
-- =====================================================================================

-- Bulk Operations (Admin only, grant required)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/%/bulk/%',
    'WRITE',
    'GLOBAL',
    ARRAY['ADMIN']::TEXT[],
    ARRAY['INTERNAL']::TEXT[],
    true,
    true,
    'v1',
    'Bulk operations require admin + grant (pattern match)',
    '{"risk_level": "high"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- Export Operations (Manager+, audit logged)
INSERT INTO policy_registry (
    id, endpoint, operation, scope, default_roles, allowed_company_types,
    requires_grant, active, policy_version, description, platform_policy,
    created_at, updated_at, created_by, updated_by, version, deleted
) VALUES (
    gen_random_uuid(),
    '/api/v1/%/export',
    'READ',
    'COMPANY',
    ARRAY['MANAGER', 'ADMIN']::TEXT[],
    NULL,
    false,
    true,
    'v1',
    'Data exports require Manager or higher',
    '{"risk_level": "medium"}'::jsonb,
    NOW(),
    NOW(),
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
) ON CONFLICT (endpoint) DO NOTHING;

-- =====================================================================================
-- NOTES
-- =====================================================================================

-- Migration Strategy:
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
-- 
-- Maintenance:
-- - Seed data specific to Company Service
-- - Other services don't need policy_registry table
-- - Clean, deterministic migrations (no conditionals)

