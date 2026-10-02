-- Intentionally verify-only (TASK-TEMPLATE-TENANCY-1 plan §2.4). Moving the catalogue rows back to
-- SYSTEM_TENANT_ID would make them invisible under RLS again and reinstate the defect found by
-- INV-RECIPE-TASK-1. Rollback verifies the migrated state and changes no data.
BEGIN;
SET LOCAL row_security = off;
DO $$
DECLARE
  n integer;
BEGIN
  SELECT count(*) INTO n FROM flowboard.task_template
   WHERE tenant_id = '00000000-0000-0000-ffff-000000000001' AND catalog_key IS NOT NULL;
  IF n <> 5 THEN
    RAISE EXCEPTION 'Cannot verify task-template catalogue: golden holds % keyed templates, expected 5', n;
  END IF;

  IF EXISTS (SELECT 1 FROM flowboard.task_template
              WHERE tenant_id = '00000000-0000-0000-0000-000000000000' AND catalog_key IS NOT NULL) THEN
    RAISE EXCEPTION 'Cannot verify task-template catalogue: SYSTEM tenant holds a keyed template';
  END IF;

  RAISE NOTICE 'Task-template catalogue retained in golden-template; rollback is intentionally a no-op';
END $$;
COMMIT;
