-- FB-2: Deactivate the orphan RECIPE_ASSIGNMENT template from V002 seed data.
-- Event type 'RecipeAssignmentNeeded' is never published; the correct event type
-- is 'WorkOrderRecipeAssignmentNeeded' (seeded in V20260602042000).
UPDATE flowboard.task_template
SET is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE event_type = 'RecipeAssignmentNeeded'
  AND task_type = 'RECIPE_ASSIGNMENT'
  AND tenant_id = '00000000-0000-0000-0000-000000000000';
