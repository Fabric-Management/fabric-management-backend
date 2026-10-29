-- Revert the application/seeder change before rollback or start-up will restore its ADMIN rule.
-- Only soft-disable unchanged rows created by this migration. Tenant-customised rows are retained.
-- No user override, generic ADMIN bypass, or unrelated permission is removed by this script.
DO $onboarding_permission_rollback_role$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND (rolsuper OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'REG-ONB-01 rollback requires an active SUPERUSER or BYPASSRLS database role'
            USING ERRCODE = '42501';
    END IF;
END;
$onboarding_permission_rollback_role$;

UPDATE common_user.permission_template
SET is_active = false, deleted_at = NOW(), updated_at = NOW(), version = version + 1
WHERE uid LIKE 'REG-ONB-01-%'
  AND resource = 'organization' AND action = 'configure'
  AND role_code = 'ADMIN' AND department_code IS NULL AND data_scope = 'ORGANIZATION'
  AND is_active = true AND deleted_at IS NULL AND version = 0;
