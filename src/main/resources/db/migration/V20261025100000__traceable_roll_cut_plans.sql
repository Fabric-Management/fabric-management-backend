-- DRAFT-HOLD-BE-3: plans are not stock; physical children appear only at verification.
ALTER TABLE production.stock_unit ADD COLUMN measurement_revision bigint NOT NULL DEFAULT 0;
CREATE FUNCTION production.bump_stock_unit_measurement_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF ROW(NEW.length,NEW.length_unit,NEW.current_weight,NEW.unit,NEW.quality_grade_id,NEW.quality_disposition,NEW.flagged)
      IS DISTINCT FROM ROW(OLD.length,OLD.length_unit,OLD.current_weight,OLD.unit,OLD.quality_grade_id,OLD.quality_disposition,OLD.flagged) THEN
    NEW.measurement_revision := OLD.measurement_revision + 1;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER stock_unit_measurement_revision BEFORE UPDATE ON production.stock_unit
  FOR EACH ROW EXECUTE FUNCTION production.bump_stock_unit_measurement_revision();

CREATE TABLE IF NOT EXISTS production.stock_unit_cut_plan (
  id uuid PRIMARY KEY, tenant_id uuid NOT NULL, uid varchar(100) UNIQUE,
  created_at timestamptz NOT NULL, created_by uuid, updated_at timestamptz NOT NULL,
  updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  target_type varchar(255) NOT NULL CHECK (target_type IN ('QUOTE', 'SALES_ORDER')),
  target_id uuid NOT NULL, target_line_id uuid NOT NULL, product_id uuid NOT NULL,
  target_fingerprint varchar(255) NOT NULL,
  delivery_quantity numeric(15,3) NOT NULL CHECK (delivery_quantity > 0),
  cut_source_id uuid NOT NULL REFERENCES production.stock_unit(id),
  sources jsonb NOT NULL CHECK (jsonb_typeof(sources) = 'array'),
  cut_delivery_quantity numeric(15,3) NOT NULL CHECK (cut_delivery_quantity > 0),
  state varchar(255) NOT NULL CHECK (state IN ('PREPARED','APPROVED','RECORDED','DISPUTED','COMPLETED','CANCELLED')),
  prepared_by uuid NOT NULL, prepared_at timestamptz NOT NULL,
  reviewed_by uuid, reviewed_at timestamptz, recorded_by uuid, recorded_at timestamptz,
  verified_by uuid, verified_at timestamptz, actual jsonb,
  delivery_stock_unit_id uuid REFERENCES production.stock_unit(id),
  remainder_stock_unit_id uuid REFERENCES production.stock_unit(id),
  disposition_reason varchar(1000),
  CHECK ((recorded_at IS NULL) = (actual IS NULL)),
  CHECK (state <> 'COMPLETED' OR (verified_at IS NOT NULL AND delivery_stock_unit_id IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cut_plan_active_line ON production.stock_unit_cut_plan(tenant_id,target_line_id)
  WHERE state NOT IN ('CANCELLED','COMPLETED') AND is_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cut_plan_active_source ON production.stock_unit_cut_plan(tenant_id,cut_source_id)
  WHERE state NOT IN ('CANCELLED','COMPLETED') AND is_active;
CREATE INDEX IF NOT EXISTS idx_cut_plan_target ON production.stock_unit_cut_plan(tenant_id,target_type,target_id,target_line_id);

CREATE TABLE IF NOT EXISTS production.stock_unit_cut_plan_operation (
  id uuid PRIMARY KEY, tenant_id uuid NOT NULL, uid varchar(100) UNIQUE,
  created_at timestamptz NOT NULL, created_by uuid, updated_at timestamptz NOT NULL,
  updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  operation_id uuid NOT NULL, actor_id uuid NOT NULL, target_line_id uuid NOT NULL,
  request_hash varchar(255) NOT NULL, result_json text NOT NULL,
  CONSTRAINT uq_cut_plan_operation UNIQUE (tenant_id,operation_id)
);
ALTER TABLE production.stock_unit_cut_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit_cut_plan FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.stock_unit_cut_plan
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
ALTER TABLE production.stock_unit_cut_plan_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit_cut_plan_operation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.stock_unit_cut_plan_operation
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE TABLE IF NOT EXISTS production.stock_unit_cut_session (
  id uuid PRIMARY KEY, tenant_id uuid NOT NULL, uid varchar(100) UNIQUE,
  created_at timestamptz NOT NULL, created_by uuid, updated_at timestamptz NOT NULL,
  updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  session_id uuid NOT NULL, actor_id uuid NOT NULL, target_type varchar(255) NOT NULL,
  target_id uuid NOT NULL, target_line_id uuid NOT NULL, product_id uuid NOT NULL,
  target_fingerprint varchar(255) NOT NULL, source_ids jsonb NOT NULL DEFAULT '[]',
  cut_source_id uuid, evidence_hash varchar(255), ended boolean NOT NULL DEFAULT false,
  previous_plan_id uuid, previous_plan_version bigint,
  UNIQUE (tenant_id,session_id)
);
CREATE INDEX IF NOT EXISTS idx_cut_session_target ON production.stock_unit_cut_session(tenant_id,target_line_id);
ALTER TABLE production.stock_unit_cut_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit_cut_session FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.stock_unit_cut_session
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Default table privileges are closed. Grant only the operations used by each runtime.
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON production.stock_unit_cut_plan,
    production.stock_unit_cut_session TO fabric_app;
  GRANT SELECT, INSERT ON production.stock_unit_cut_plan_operation TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL;
END $$;

DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON production.stock_unit_cut_plan,
    production.stock_unit_cut_session TO fabric_system;
  GRANT SELECT, INSERT, DELETE ON production.stock_unit_cut_plan_operation TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL;
END $$;
