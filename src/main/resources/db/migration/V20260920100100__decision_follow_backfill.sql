-- Reconstruct the same automatic facts as the listeners, strictly from durable source evidence.
SET LOCAL row_security = off;

INSERT INTO flowboard.decision_follow
    (id, tenant_id, uid, created_at, created_by, updated_at, updated_by, is_active,
     deleted_at, version, case_id, user_id, source, source_ref)
SELECT gen_random_uuid(), r.tenant_id, gen_random_uuid()::text, r.recorded_at,
       '00000000-0000-0000-0000-000000000000'::uuid, r.recorded_at,
       '00000000-0000-0000-0000-000000000000'::uuid, true, null, 0,
       r.case_id, r.actor_id, 'SETTLED', r.id
FROM (
    SELECT DISTINCT ON (tenant_id, case_id, actor_id)
           tenant_id, case_id, actor_id, id, recorded_at
    FROM sales_ord.order_cover_result
    WHERE actor_kind = 'USER'
      AND actor_id <> '00000000-0000-0000-0000-000000000000'::uuid
    ORDER BY tenant_id, case_id, actor_id, recorded_at, id
) r
JOIN common_user.common_user u
  ON u.tenant_id = r.tenant_id AND u.id = r.actor_id
ON CONFLICT (tenant_id, case_id, user_id, source) DO NOTHING;

INSERT INTO flowboard.decision_follow
    (id, tenant_id, uid, created_at, created_by, updated_at, updated_by, is_active,
     deleted_at, version, case_id, user_id, source, source_ref)
SELECT gen_random_uuid(), a.tenant_id, gen_random_uuid()::text, a.assigned_at,
       '00000000-0000-0000-0000-000000000000'::uuid, a.assigned_at,
       '00000000-0000-0000-0000-000000000000'::uuid, true, null, 0,
       c.id, a.user_id, 'ASSIGNED', a.id
FROM (
    SELECT DISTINCT ON (tenant_id, task_id, user_id)
           tenant_id, task_id, user_id, id, assigned_at
    FROM flowboard.task_assignee
    WHERE user_id IS NOT NULL
      AND user_id <> '00000000-0000-0000-0000-000000000000'::uuid
    ORDER BY tenant_id, task_id, user_id, assigned_at, id
) a
JOIN sales_ord.order_cover_case c
  ON c.tenant_id = a.tenant_id AND c.task_id = a.task_id
JOIN common_user.common_user u
  ON u.tenant_id = a.tenant_id AND u.id = a.user_id
ON CONFLICT (tenant_id, case_id, user_id, source) DO NOTHING;

INSERT INTO flowboard.decision_follow
    (id, tenant_id, uid, created_at, created_by, updated_at, updated_by, is_active,
     deleted_at, version, case_id, user_id, source, source_ref)
SELECT gen_random_uuid(), c.tenant_id, gen_random_uuid()::text, c.created_at,
       '00000000-0000-0000-0000-000000000000'::uuid, c.created_at,
       '00000000-0000-0000-0000-000000000000'::uuid, true, null, 0,
       c.id, o.created_by, 'OPENED', null
FROM sales_ord.order_cover_case c
JOIN sales_ord.sales_order o
  ON o.tenant_id = c.tenant_id AND o.id = c.sales_order_id
JOIN common_user.common_user u
  ON u.tenant_id = c.tenant_id AND u.id = o.created_by AND u.is_active
WHERE o.created_by IS NOT NULL
  AND o.created_by <> '00000000-0000-0000-0000-000000000000'::uuid
ON CONFLICT (tenant_id, case_id, user_id, source) DO NOTHING;
