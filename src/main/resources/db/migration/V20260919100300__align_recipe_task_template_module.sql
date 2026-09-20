-- PRODUCTION is a TaskType, not a valid Flowboard ModuleType. The recipe template predates
-- the current module catalogue and otherwise fails during JPA hydration before task creation.
UPDATE flowboard.task_template
SET module_type = 'GENERAL',
    updated_at = CURRENT_TIMESTAMP
WHERE event_type = 'WorkOrderRecipeAssignmentNeeded'
  AND task_type = 'RECIPE_ASSIGNMENT'
  AND module_type = 'PRODUCTION';
