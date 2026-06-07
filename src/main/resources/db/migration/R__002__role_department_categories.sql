-- ============================================================================
-- R002: Seed System Roles (Repeatable)
-- ============================================================================
-- Seeds platform roles under the TEMPLATE tenant for per-tenant cloning.
-- During onboarding, CloneTemplateRolesStep clones these to the new tenant.
-- Roles are generic — department provides organizational context.
--
-- Scopes:
--   INTERNAL = Tenant's own employees
--   PARTNER  = Trading partner users
--   SYSTEM   = Platform admin (hidden from tenant UI)
-- ============================================================================

DO $$
DECLARE
    template_tenant_id UUID := '00000000-0000-0000-ffff-000000000001'::UUID;
BEGIN
    INSERT INTO common_user.common_role
        (tenant_id, uid, role_name, role_code, description, role_scope, is_system_role, display_order, is_active)
    VALUES
        -- SYSTEM scope (hidden from tenant UI)
        (template_tenant_id, 'SYS-ROLE-0001', 'Platform Administrator', 'PLATFORM_ADMIN', 'Full platform access — hidden from tenants', 'SYSTEM', TRUE, 0, TRUE),

        -- INTERNAL scope (tenant employee roles)
        (template_tenant_id, 'SYS-ROLE-004', 'Administrator', 'ADMIN', 'Full tenant access — manage everything', 'INTERNAL', TRUE, 1, TRUE),
        (template_tenant_id, 'SYS-ROLE-008', 'Manager', 'MANAGER', 'Department-level management', 'INTERNAL', TRUE, 2, TRUE),
        (template_tenant_id, 'SYS-ROLE-009', 'Supervisor', 'SUPERVISOR', 'Team/shift leadership', 'INTERNAL', TRUE, 3, TRUE),
        (template_tenant_id, 'SYS-ROLE-014', 'Worker', 'WORKER', 'Standard employee', 'INTERNAL', TRUE, 4, TRUE),
        (template_tenant_id, 'SYS-ROLE-015', 'Viewer', 'VIEWER', 'Read-only access', 'INTERNAL', TRUE, 5, TRUE),

        -- PARTNER scope (trading partner roles)
        (template_tenant_id, 'SYS-ROLE-010', 'Partner Owner', 'PARTNER_OWNER', 'Partner company owner — full access + user management', 'PARTNER', TRUE, 10, TRUE),
        (template_tenant_id, 'SYS-ROLE-011', 'Partner Accountant', 'PARTNER_ACCOUNTANT', 'Partner accountant — invoices, balance, payment history', 'PARTNER', TRUE, 11, TRUE),
        (template_tenant_id, 'SYS-ROLE-012', 'Partner Buyer', 'PARTNER_BUYER', 'Partner buyer — order creation and tracking', 'PARTNER', TRUE, 12, TRUE),
        (template_tenant_id, 'SYS-ROLE-013', 'Partner Viewer', 'PARTNER_VIEWER', 'Partner viewer — read-only access', 'PARTNER', TRUE, 13, TRUE)
    ON CONFLICT (uid) DO UPDATE SET
        role_name = EXCLUDED.role_name,
        role_code = EXCLUDED.role_code,
        description = EXCLUDED.description,
        role_scope = EXCLUDED.role_scope,
        display_order = EXCLUDED.display_order,
        is_active = EXCLUDED.is_active;

    -- Deactivate deprecated department-specific roles
    UPDATE common_user.common_role
    SET is_active = FALSE
    WHERE tenant_id = template_tenant_id
      AND role_code IN ('PROD_MANAGER', 'PROD_WORKER', 'HR_MANAGER', 'LOG_MANAGER', 'WAREHOUSE_WORKER', 'QC');

END $$;
