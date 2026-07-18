-- ADR-0010 compensating rollback. Valid only inside the maintenance window before writes reopen.
-- DDL deliberately remains in place so the old binary can validate against the additive schema.

BEGIN;

UPDATE production.production_execution_batch_attribute source
SET is_active = archive.prev_is_active,
    deleted_at = archive.prev_deleted_at,
    created_at = archive.prev_created_at,
    created_by = archive.prev_created_by,
    updated_at = archive.prev_updated_at,
    updated_by = archive.prev_updated_by,
    version = archive.prev_version
FROM production.production_execution_batch_color_archive archive
WHERE source.tenant_id = archive.tenant_id
  AND source.id = archive.source_row_id
  AND archive.cutover_version = 'V20260718120000';

-- Clear EVERY non-NULL color_id, not only archived batches: preflight #6 guaranteed no value
-- existed before the cutover, so any value present now was written by the cutover or by
-- in-window smoke tests (create/PATCH). Leaving smoke-test values behind would make a later
-- re-attempt migration fail its own preflight #6. Clearing all is lossless by construction.
UPDATE production.production_execution_batch
SET color_id = NULL
WHERE color_id IS NOT NULL;

DELETE FROM production.production_execution_batch_color_archive
WHERE cutover_version = 'V20260718120000';

COMMIT;
