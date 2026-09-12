-- PERM-CAT-2 operational rollback. Run transactionally after stopping application writes.
-- Restores only flags marked by this migration, including the original deleted_at
-- of active-but-deleted rows. Unmarked rows and version remain unchanged.
-- updated_at records this rollback. Columns stay; markers are not an audit log.
-- Does not restore PermissionKey, GrantRules or the OpenAPI contract.

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

UPDATE common_user.permission_template
   SET is_active = true, deleted_at = NULL, retired_by = NULL, updated_at = NOW()
 WHERE retired_by = 'PERM-CAT-2';

UPDATE common_user.permission_template
   SET deleted_at = NULL, retired_by = NULL, updated_at = NOW()
 WHERE retired_by = 'PERM-CAT-2:inactive';

UPDATE common_user.permission_template
   SET is_active = true, retired_by = NULL, updated_at = NOW()
 WHERE retired_by = 'PERM-CAT-2:deleted';

UPDATE common_user.permission_override
   SET is_active = true, deleted_at = NULL, retired_by = NULL, updated_at = NOW()
 WHERE retired_by = 'PERM-CAT-2';

UPDATE common_user.permission_override
   SET deleted_at = NULL, retired_by = NULL, updated_at = NOW()
 WHERE retired_by = 'PERM-CAT-2:inactive';

UPDATE common_user.permission_override
   SET is_active = true, retired_by = NULL, updated_at = NOW()
 WHERE retired_by = 'PERM-CAT-2:deleted';

