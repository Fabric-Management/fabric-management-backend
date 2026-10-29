-- REG-ONB-01: existing real tenants need the same ADMIN-only company setup rule as new ones.
-- PermissionTemplateSeeder is the sole writer of the golden template tenant; do not write it here.
-- Preserve every existing row (including inactive/deleted/custom-scoped rows) and user override.
DO $onboarding_permission_role$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND (rolsuper OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'REG-ONB-01 requires an active SUPERUSER or BYPASSRLS database role'
            USING ERRCODE = '42501';
    END IF;
END;
$onboarding_permission_role$;

INSERT INTO common_user.permission_template (
    id, tenant_id, uid, role_code, department_code, resource, action,
    data_scope, is_active, created_at, updated_at, version
)
SELECT gen_random_uuid(), t.id, 'REG-ONB-01-' || gen_random_uuid()::text,
       'ADMIN', NULL, 'organization', 'configure', 'ORGANIZATION', true, NOW(), NOW(), 0
FROM common_tenant.common_tenant t
WHERE t.deleted_at IS NULL
  AND t.id NOT IN ('00000000-0000-0000-0000-000000000000'::uuid,
                   '00000000-0000-0000-ffff-000000000001'::uuid)
  AND NOT EXISTS (
      SELECT 1 FROM common_user.permission_template p
      WHERE p.tenant_id = t.id AND p.role_code = 'ADMIN' AND p.department_code IS NULL
        AND p.resource = 'organization' AND p.action = 'configure'
  )
ON CONFLICT DO NOTHING;

-- Do not widen MANAGER/SUPERVISOR/WORKER/VIEWER grants. Delegation remains explicit tenant policy.
-- Company commands read fresh permissions, so migration/start-up cannot leave a cached grant live.
