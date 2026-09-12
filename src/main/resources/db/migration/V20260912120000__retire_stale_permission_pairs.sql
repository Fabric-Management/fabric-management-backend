-- PERM-CAT-2. Deploy FE-ARCH-3 before applying this migration.
-- Retire unused pairs in every tenant, including the template tenant. No live
-- enforcement point consumes these pairs; grants for retained pairs do not change.
-- The evaluator reads resource/action strings, not PermissionKey enum values, so
-- archived or tenant-customised rows cannot fail enum deserialization at read time.
-- Evaluator and cloner filter is_active; backfill filters deleted_at instead.
-- Four prior states (active, deleted_at):
-- true/NULL -> false/NOW(), marker PERM-CAT-2;
-- false/NULL -> false/NOW(), marker PERM-CAT-2:inactive;
-- true/set -> false/same timestamp, marker PERM-CAT-2:deleted;
-- false/set -> untouched. Markers preserve the prior flags for operational rollback.
-- updated_at advances on changes; version is unchanged. No physical row is deleted.

-- FORCE RLS also applies to table owners. Refuse a filtered all-tenant operation.
-- Check the active role (current_user), not the login role before a SET ROLE.
DO $perm_cat_2_role$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND (rolsuper OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'PERM-CAT-2 requires an active SUPERUSER or BYPASSRLS database role; current role: %', current_user
            USING ERRCODE = '42501';
    END IF;
END;
$perm_cat_2_role$;

ALTER TABLE common_user.permission_template
    ADD COLUMN IF NOT EXISTS retired_by VARCHAR(32);

ALTER TABLE common_user.permission_override
    ADD COLUMN IF NOT EXISTS retired_by VARCHAR(32);

UPDATE common_user.permission_template
   SET is_active = false, deleted_at = NOW(), retired_by = 'PERM-CAT-2', updated_at = NOW()
 WHERE (resource, action) IN (
        ('admin', 'access'),
        ('dashboard', 'view'),
        ('fiber', 'approve'),
        ('flowboard', 'edit'),
        ('flowboard', 'manage'),
        ('flowboard', 'view'),
        ('notifications', 'view'),
        ('partners', 'read'),
        ('partners', 'write'),
        ('projects', 'manage'),
        ('projects', 'read'),
        ('projects', 'write'),
        ('reports', 'export'),
        ('reports', 'view'),
        ('settings', 'manage'),
        ('settings', 'view'),
        ('settings', 'write')
   )
   AND is_active = true AND deleted_at IS NULL
   AND retired_by IS NULL;

UPDATE common_user.permission_template
   SET deleted_at = NOW(), retired_by = 'PERM-CAT-2:inactive', updated_at = NOW()
 WHERE (resource, action) IN (
        ('admin', 'access'),
        ('dashboard', 'view'),
        ('fiber', 'approve'),
        ('flowboard', 'edit'),
        ('flowboard', 'manage'),
        ('flowboard', 'view'),
        ('notifications', 'view'),
        ('partners', 'read'),
        ('partners', 'write'),
        ('projects', 'manage'),
        ('projects', 'read'),
        ('projects', 'write'),
        ('reports', 'export'),
        ('reports', 'view'),
        ('settings', 'manage'),
        ('settings', 'view'),
        ('settings', 'write')
   )
   AND is_active = false AND deleted_at IS NULL
   AND retired_by IS NULL;

UPDATE common_user.permission_template
   SET is_active = false, retired_by = 'PERM-CAT-2:deleted', updated_at = NOW()
 WHERE (resource, action) IN (
        ('admin', 'access'),
        ('dashboard', 'view'),
        ('fiber', 'approve'),
        ('flowboard', 'edit'),
        ('flowboard', 'manage'),
        ('flowboard', 'view'),
        ('notifications', 'view'),
        ('partners', 'read'),
        ('partners', 'write'),
        ('projects', 'manage'),
        ('projects', 'read'),
        ('projects', 'write'),
        ('reports', 'export'),
        ('reports', 'view'),
        ('settings', 'manage'),
        ('settings', 'view'),
        ('settings', 'write')
   )
   AND is_active = true AND deleted_at IS NOT NULL
   AND retired_by IS NULL;

UPDATE common_user.permission_override
   SET is_active = false, deleted_at = NOW(), retired_by = 'PERM-CAT-2', updated_at = NOW()
 WHERE (resource, action) IN (
        ('admin', 'access'),
        ('dashboard', 'view'),
        ('fiber', 'approve'),
        ('flowboard', 'edit'),
        ('flowboard', 'manage'),
        ('flowboard', 'view'),
        ('notifications', 'view'),
        ('partners', 'read'),
        ('partners', 'write'),
        ('projects', 'manage'),
        ('projects', 'read'),
        ('projects', 'write'),
        ('reports', 'export'),
        ('reports', 'view'),
        ('settings', 'manage'),
        ('settings', 'view'),
        ('settings', 'write')
   )
   AND is_active = true AND deleted_at IS NULL
   AND retired_by IS NULL;

UPDATE common_user.permission_override
   SET deleted_at = NOW(), retired_by = 'PERM-CAT-2:inactive', updated_at = NOW()
 WHERE (resource, action) IN (
        ('admin', 'access'),
        ('dashboard', 'view'),
        ('fiber', 'approve'),
        ('flowboard', 'edit'),
        ('flowboard', 'manage'),
        ('flowboard', 'view'),
        ('notifications', 'view'),
        ('partners', 'read'),
        ('partners', 'write'),
        ('projects', 'manage'),
        ('projects', 'read'),
        ('projects', 'write'),
        ('reports', 'export'),
        ('reports', 'view'),
        ('settings', 'manage'),
        ('settings', 'view'),
        ('settings', 'write')
   )
   AND is_active = false AND deleted_at IS NULL
   AND retired_by IS NULL;

UPDATE common_user.permission_override
   SET is_active = false, retired_by = 'PERM-CAT-2:deleted', updated_at = NOW()
 WHERE (resource, action) IN (
        ('admin', 'access'),
        ('dashboard', 'view'),
        ('fiber', 'approve'),
        ('flowboard', 'edit'),
        ('flowboard', 'manage'),
        ('flowboard', 'view'),
        ('notifications', 'view'),
        ('partners', 'read'),
        ('partners', 'write'),
        ('projects', 'manage'),
        ('projects', 'read'),
        ('projects', 'write'),
        ('reports', 'export'),
        ('reports', 'view'),
        ('settings', 'manage'),
        ('settings', 'view'),
        ('settings', 'write')
   )
   AND is_active = true AND deleted_at IS NOT NULL
   AND retired_by IS NULL;

