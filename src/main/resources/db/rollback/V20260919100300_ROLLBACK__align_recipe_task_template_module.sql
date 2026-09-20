-- Intentionally forward-only. V20260602042000 stored PRODUCTION as a module type even though it
-- is a TaskType and cannot be hydrated as flowboard.task.domain.ModuleType. Restoring that value
-- would reinstate the production defect. Rollback verifies the repaired state and changes no data.
BEGIN;
SET LOCAL row_security = off;
DO $$
BEGIN
  IF EXISTS (
      SELECT 1
      FROM flowboard.task_template
      WHERE event_type = 'WorkOrderRecipeAssignmentNeeded'
        AND task_type = 'RECIPE_ASSIGNMENT'
        AND module_type = 'PRODUCTION') THEN
    RAISE EXCEPTION
      'Cannot roll back recipe task-template module repair: invalid PRODUCTION module remains';
  END IF;

  RAISE NOTICE
    'Recipe task-template module repair retained; rollback is intentionally a no-op';
END $$;
COMMIT;
