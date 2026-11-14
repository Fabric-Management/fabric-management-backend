-- The root organisation owns the display name. Report ambiguous historical records for review.
CREATE FUNCTION common_company.backfill_tenant_company_names()
RETURNS TABLE (tenant_id uuid, reason text)
LANGUAGE sql
-- Fail if the migration role would see a filtered view of either table.
SET row_security = off
AS $$
    WITH roots AS (
        SELECT o.tenant_id, count(*) AS root_count, min(o.name) AS root_name
        FROM common_company.common_organization o
        WHERE o.parent_organization_id IS NULL
          AND o.is_active = true
          AND o.organization_type <> 'EXTERNAL_PARTNER'
        GROUP BY o.tenant_id
    ), candidates AS (
        SELECT t.id, r.root_name,
               CASE
                   WHEN r.root_count IS NULL THEN 'NO_ROOT'
                   WHEN r.root_count <> 1 THEN 'MULTIPLE_ROOTS'
                   WHEN r.root_name IS NULL OR r.root_name !~ '[^[:space:]]' THEN 'BLANK_ROOT_NAME'
               END AS issue
        FROM common_tenant.common_tenant t
        LEFT JOIN roots r ON r.tenant_id = t.id
        WHERE t.type = 'REGULAR'
          AND t.deleted_at IS NULL
          AND t.id NOT IN ('00000000-0000-0000-0000-000000000000',
                           '00000000-0000-0000-ffff-000000000001')
    ), corrected AS (
        UPDATE common_tenant.common_tenant t
        SET name = c.root_name, updated_at = now(), version = t.version + 1
        FROM candidates c
        WHERE t.id = c.id AND c.issue IS NULL AND t.name IS DISTINCT FROM c.root_name
    )
    SELECT c.id, c.issue FROM candidates c WHERE c.issue IS NOT NULL;
$$;

REVOKE ALL ON FUNCTION common_company.backfill_tenant_company_names() FROM PUBLIC;

DO $$
DECLARE
    issue record;
BEGIN
    FOR issue IN SELECT * FROM common_company.backfill_tenant_company_names() LOOP
        RAISE NOTICE 'Company name backfill skipped tenant %: %', issue.tenant_id, issue.reason;
    END LOOP;
END;
$$;
