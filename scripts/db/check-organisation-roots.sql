-- Run as the migration role against the target database before deploying TD-20.
BEGIN READ ONLY;
SET LOCAL row_security = off;

WITH roots AS (
    SELECT tenant_id, count(*) AS root_count, array_agg(id ORDER BY id) AS root_ids
    FROM common_company.common_organization
    WHERE parent_organization_id IS NULL AND is_active = true
      AND organization_type <> 'EXTERNAL_PARTNER'
    GROUP BY tenant_id
)
SELECT r.tenant_id, 'MULTIPLE_ROOTS' AS issue, r.root_ids
FROM roots r
WHERE r.root_count > 1
UNION ALL
SELECT t.id, 'NO_ROOT', ARRAY[]::uuid[]
FROM common_tenant.common_tenant t
LEFT JOIN roots r ON r.tenant_id = t.id
WHERE t.type = 'REGULAR' AND t.is_active = true AND t.deleted_at IS NULL
  AND t.id NOT IN ('00000000-0000-0000-0000-000000000000',
                   '00000000-0000-0000-ffff-000000000001')
  AND r.tenant_id IS NULL
ORDER BY issue, tenant_id;

COMMIT;
