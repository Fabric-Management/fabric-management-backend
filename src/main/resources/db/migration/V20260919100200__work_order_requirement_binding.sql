SET LOCAL lock_timeout='5s';
ALTER TABLE production.prod_work_order
  ADD COLUMN requirement_profile_id UUID,
  ADD COLUMN requirement_profile_version INTEGER,
  ADD COLUMN requirement_profile_snapshot JSONB;
ALTER TABLE production.prod_work_order ADD CONSTRAINT chk_work_order_requirement_binding CHECK (
  (requirement_profile_id IS NULL AND requirement_profile_version IS NULL AND requirement_profile_snapshot IS NULL)
  OR (requirement_profile_id IS NOT NULL AND requirement_profile_version IS NOT NULL
      AND requirement_profile_version > 0 AND requirement_profile_snapshot IS NOT NULL
      AND jsonb_typeof(requirement_profile_snapshot)='object'));
ALTER TABLE production.prod_work_order ADD CONSTRAINT fk_work_order_requirement_profile
  FOREIGN KEY(tenant_id,requirement_profile_id,requirement_profile_version)
  REFERENCES sales_ord.requirement_profile_version(tenant_id,profile_id,profile_version);

CREATE FUNCTION production.reject_work_order_requirement_binding_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.requirement_profile_id IS NOT NULL AND
     (NEW.requirement_profile_id,NEW.requirement_profile_version,NEW.requirement_profile_snapshot)
       IS DISTINCT FROM
     (OLD.requirement_profile_id,OLD.requirement_profile_version,OLD.requirement_profile_snapshot) THEN
    RAISE EXCEPTION 'Work-order requirement binding is immutable' USING ERRCODE='55000';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER work_order_requirement_binding_immutable
  BEFORE UPDATE OF requirement_profile_id,requirement_profile_version,requirement_profile_snapshot
  ON production.prod_work_order FOR EACH ROW
  EXECUTE FUNCTION production.reject_work_order_requirement_binding_change();
