-- Intentionally verify-only (TASK-TEMPLATE-TENANCY-1 plan §2.4). The column is nullable and the
-- partial unique index covers keyed rows only, so the previous application version runs unchanged
-- against this schema: its inserts leave catalog_key NULL and its router query ignores it.
-- Dropping the column would strip the identity of every tenant copy; a later forward migration
-- would then report them all as R3 findings. Rollback = application rollback; data is kept.
BEGIN;
SET LOCAL row_security = off;
DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
       WHERE table_schema = 'flowboard' AND table_name = 'task_template'
         AND column_name = 'catalog_key') THEN
    RAISE EXCEPTION 'Cannot verify task-template catalogue key: column catalog_key is missing';
  END IF;

  RAISE NOTICE 'task_template.catalog_key retained; rollback is intentionally a no-op';
END $$;
COMMIT;
