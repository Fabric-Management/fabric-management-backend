-- The Flyway role must see every tenant, including with FORCE ROW LEVEL SECURITY.
SET LOCAL row_security = off;
SET LOCAL lock_timeout = '5s';

-- Prevent writes between the diagnostic scan and the index build.
LOCK TABLE common_company.common_organization IN SHARE MODE;

DO $$
DECLARE
    duplicates text;
    missing_root record;
BEGIN
    SELECT string_agg(format('%s: %s', tenant_id, root_ids), E'\n' ORDER BY tenant_id)
    INTO duplicates
    FROM (
        SELECT tenant_id, array_agg(id ORDER BY id) AS root_ids
        FROM common_company.common_organization
        WHERE parent_organization_id IS NULL AND is_active = true
          AND organization_type <> 'EXTERNAL_PARTNER'
        GROUP BY tenant_id
        HAVING count(*) > 1
    ) conflicts;

    FOR missing_root IN
        SELECT t.id
        FROM common_tenant.common_tenant t
        WHERE t.type = 'REGULAR' AND t.is_active = true AND t.deleted_at IS NULL
          AND t.id NOT IN ('00000000-0000-0000-0000-000000000000',
                           '00000000-0000-0000-ffff-000000000001')
          AND NOT EXISTS (
              SELECT 1
              FROM common_company.common_organization o
              WHERE o.tenant_id = t.id AND o.parent_organization_id IS NULL
                AND o.is_active = true AND o.organization_type <> 'EXTERNAL_PARTNER'
          )
        ORDER BY t.id
    LOOP
        RAISE WARNING 'Active customer tenant % has no active internal root organisation',
            missing_root.id;
    END LOOP;

    IF duplicates IS NOT NULL THEN
        RAISE EXCEPTION 'Multiple active internal root organisations found'
            USING DETAIL = duplicates,
                  HINT = 'Verify the company and branch relationships before retrying this migration.';
    END IF;
END;
$$;

CREATE UNIQUE INDEX ux_organization_active_company_root
    ON common_company.common_organization (tenant_id)
    WHERE parent_organization_id IS NULL AND is_active = true
      AND organization_type <> 'EXTERNAL_PARTNER';
