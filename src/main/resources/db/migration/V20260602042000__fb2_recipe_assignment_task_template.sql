INSERT INTO flowboard.task_template (
    id, tenant_id, uid, name, event_type, title_template, task_type, 
    module_type, default_priority, default_assignee_role, 
    estimated_hours, auto_labels, is_active, 
    created_at, updated_at, version
) VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000000',
    'SYS-TMPL-RECIPE-ASSIGN',
    'Recipe Assignment Required',
    'WorkOrderRecipeAssignmentNeeded',
    'Recipe atanmalı — {certificationReq} / {originReq}',
    'RECIPE_ASSIGNMENT',
    'PRODUCTION',
    'HIGH',
    'MANAGER',
    1.00,
    'RECIPE_REQUIRED',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
) ON CONFLICT (uid) DO NOTHING;
