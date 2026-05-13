-- FAZ 9/12: Insert initial Approval Policy for WORK_ORDER

-- Apply this to all existing tenants
INSERT INTO common_approval.approval_policy (
    id, tenant_id, uid, entity_type, required_for_level, approver_role, promotion_threshold, is_active, created_by
)
SELECT 
    gen_random_uuid(),
    t.id,
    'SYS-000-PO-' || UPPER(SUBSTRING(CAST(gen_random_uuid() AS VARCHAR), 1, 8)),
    'WORK_ORDER',
    'PROBATION',
    'MANAGER',
    10,
    true,
    NULL
FROM common_tenant.common_tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM common_approval.approval_policy p 
    WHERE p.tenant_id = t.id AND p.entity_type = 'WORK_ORDER' AND p.required_for_level = 'PROBATION'
);
