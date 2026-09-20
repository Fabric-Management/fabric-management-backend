-- Stop work-order writers first. Refuse to discard requirement bindings of existing work orders.
-- One transaction, guard evaluated with row_security off (see the order-cover settlement rollback).
BEGIN;
SET LOCAL row_security = off;
LOCK TABLE production.prod_work_order IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM production.prod_work_order
      WHERE requirement_profile_id IS NOT NULL
         OR requirement_profile_version IS NOT NULL
         OR requirement_profile_snapshot IS NOT NULL) THEN
    RAISE EXCEPTION
      'Cannot roll back work-order requirement binding while bound work orders exist';
  END IF;
END $$;

DROP TRIGGER IF EXISTS work_order_requirement_binding_immutable ON production.prod_work_order;
DROP FUNCTION IF EXISTS production.reject_work_order_requirement_binding_change();
ALTER TABLE production.prod_work_order DROP CONSTRAINT IF EXISTS chk_work_order_requirement_binding;
ALTER TABLE production.prod_work_order DROP CONSTRAINT IF EXISTS fk_work_order_requirement_profile;
ALTER TABLE production.prod_work_order DROP COLUMN IF EXISTS requirement_profile_snapshot;
ALTER TABLE production.prod_work_order DROP COLUMN IF EXISTS requirement_profile_version;
ALTER TABLE production.prod_work_order DROP COLUMN IF EXISTS requirement_profile_id;
COMMIT;
