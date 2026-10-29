-- PAYMENT-PLAN-1 S2: commercial expectations, not accounting payments.
ALTER TABLE sales_ord.sales_order
  ADD COLUMN payment_plan_managed boolean NOT NULL DEFAULT false,
  ADD COLUMN payment_note varchar(2000);
CREATE TABLE IF NOT EXISTS sales_ord.order_payment_plan (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  sales_order_id uuid NOT NULL,
  current_draft_revision_id uuid, active_agreed_revision_id uuid,
  confirmation_date date, last_revision_no integer NOT NULL DEFAULT 0,
  cancelled boolean NOT NULL DEFAULT false,
  UNIQUE (tenant_id, id), UNIQUE (tenant_id, sales_order_id),
  FOREIGN KEY (tenant_id, sales_order_id) REFERENCES sales_ord.sales_order(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS sales_ord.order_payment_plan_revision (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  plan_id uuid NOT NULL, revision_no integer NOT NULL CHECK (revision_no > 0),
  source_order_version bigint NOT NULL, pricing_fingerprint varchar(64) NOT NULL,
  operation_id uuid NOT NULL, request_hash varchar(64) NOT NULL, actor_id uuid NOT NULL,
  change_reason varchar(1000), state varchar(20) NOT NULL CHECK (state IN ('DRAFT','PROPOSED','AGREED','SUPERSEDED','CANCELLED')),
  approval_version_id uuid, agreed_approval_id uuid, agreed_at timestamptz,
  activated_at timestamptz, activation_date date, cancelled_at timestamptz,
  result_order_version bigint NOT NULL DEFAULT 0, validation_issues jsonb NOT NULL DEFAULT '[]',
  UNIQUE (tenant_id, id), UNIQUE (tenant_id, plan_id, id),
  UNIQUE (tenant_id, plan_id, revision_no), UNIQUE (tenant_id, plan_id, operation_id),
  CONSTRAINT fk_payment_revision_plan FOREIGN KEY (tenant_id, plan_id) REFERENCES sales_ord.order_payment_plan(tenant_id, id)
    DEFERRABLE INITIALLY DEFERRED,
  CHECK ((agreed_at IS NULL) = (agreed_approval_id IS NULL)),
  CHECK (activation_date IS NULL OR activated_at IS NOT NULL)
);
ALTER TABLE sales_ord.order_payment_plan
  ADD CONSTRAINT fk_payment_plan_current_revision FOREIGN KEY (tenant_id, id, current_draft_revision_id)
    REFERENCES sales_ord.order_payment_plan_revision(tenant_id, plan_id, id) DEFERRABLE INITIALLY DEFERRED,
  ADD CONSTRAINT fk_payment_plan_agreed_revision FOREIGN KEY (tenant_id, id, active_agreed_revision_id)
    REFERENCES sales_ord.order_payment_plan_revision(tenant_id, plan_id, id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE IF NOT EXISTS sales_ord.order_payment_plan_currency (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  revision_id uuid NOT NULL, currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  base_amount numeric(19,4) CHECK (base_amount >= 0), rules jsonb,
  UNIQUE (tenant_id, id), UNIQUE (tenant_id, revision_id, currency),
  FOREIGN KEY (tenant_id, revision_id) REFERENCES sales_ord.order_payment_plan_revision(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS sales_ord.order_payment_plan_line (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  currency_group_id uuid NOT NULL, obligation_id uuid NOT NULL,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  kind varchar(20) NOT NULL CHECK (kind IN ('DOWN_PAYMENT','INSTALMENT')),
  amount numeric(19,4) CHECK (amount >= 0),
  method varchar(20) CHECK (method IN ('CASH','BANK_TRANSFER','CHEQUE','CREDIT_CARD','OTHER')),
  date_trigger varchar(30) CHECK (date_trigger IN ('EXPLICIT_DATE','ON_ORDER_CONFIRMATION')),
  expected_date date, cheque_payment_date date, manual boolean NOT NULL DEFAULT false,
  UNIQUE (tenant_id, currency_group_id, ordinal), UNIQUE (tenant_id, currency_group_id, obligation_id),
  FOREIGN KEY (tenant_id, currency_group_id) REFERENCES sales_ord.order_payment_plan_currency(tenant_id, id),
  CHECK (date_trigger IS DISTINCT FROM 'ON_ORDER_CONFIRMATION' OR (kind = 'DOWN_PAYMENT' AND expected_date IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_payment_plan_revisions ON sales_ord.order_payment_plan_revision(tenant_id, plan_id, revision_no DESC);
CREATE INDEX IF NOT EXISTS idx_payment_plan_lines_date ON sales_ord.order_payment_plan_line(tenant_id, expected_date);
CREATE INDEX IF NOT EXISTS idx_payment_plan_obligation ON sales_ord.order_payment_plan_line(tenant_id, obligation_id);
ALTER TABLE sales_ord.order_payment_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_payment_plan FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_payment_plan
 USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
 WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
 GRANT SELECT, INSERT, UPDATE ON sales_ord.order_payment_plan TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE sales_ord.order_payment_plan_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_payment_plan_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_payment_plan_revision
 USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
 WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
 GRANT SELECT, INSERT, UPDATE ON sales_ord.order_payment_plan_revision TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE sales_ord.order_payment_plan_currency ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_payment_plan_currency FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_payment_plan_currency
 USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
 WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
 GRANT SELECT, INSERT ON sales_ord.order_payment_plan_currency TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE sales_ord.order_payment_plan_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_payment_plan_line FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_payment_plan_line
 USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
 WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
 GRANT SELECT, INSERT ON sales_ord.order_payment_plan_line TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ DECLARE constraint_name text; BEGIN
 FOR constraint_name IN SELECT conname FROM pg_constraint
 WHERE conrelid = 'sales_ord.order_version'::regclass AND contype = 'c'
   AND pg_get_constraintdef(oid) LIKE '%kind%' AND pg_get_constraintdef(oid) LIKE '%INFORMATION%'
 LOOP EXECUTE format('ALTER TABLE sales_ord.order_version DROP CONSTRAINT %I', constraint_name); END LOOP;
END $$;
ALTER TABLE sales_ord.order_version ADD CONSTRAINT ck_order_version_kind_payment_plan
 CHECK (kind IN ('INFORMATION','APPROVAL','PAYMENT_PLAN'));
ALTER TABLE sales_ord.order_payment_plan_revision ADD CONSTRAINT fk_payment_revision_version
 FOREIGN KEY (tenant_id, approval_version_id) REFERENCES sales_ord.order_version(tenant_id, id)
 DEFERRABLE INITIALLY DEFERRED;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_plan_approval_identity ON sales_ord.customer_approval(tenant_id, id);
ALTER TABLE sales_ord.order_payment_plan_revision ADD CONSTRAINT fk_payment_revision_agreement
 FOREIGN KEY (tenant_id, agreed_approval_id) REFERENCES sales_ord.customer_approval(tenant_id, id)
 DEFERRABLE INITIALLY DEFERRED;
DO $$ BEGIN
 GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.order_payment_plan TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
 GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.order_payment_plan_revision TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
 GRANT SELECT, INSERT, DELETE ON sales_ord.order_payment_plan_currency TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
 GRANT SELECT, INSERT, DELETE ON sales_ord.order_payment_plan_line TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

--
-- A payment plan revision may be submitted below cash that finance has already recorded, but only
-- after the sender says so. Until now that statement lived in the application log. This is its
-- durable record: one append-only row per revision, naming who acknowledged, when, and exactly which
-- obligations the revision lowered below recorded cash (a snapshot, so later matches cannot rewrite it).

CREATE TABLE IF NOT EXISTS sales_ord.order_payment_plan_settlement_ack (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  sales_order_id uuid NOT NULL, plan_id uuid NOT NULL, revision_id uuid NOT NULL,
  actor_id uuid NOT NULL, acknowledged_at timestamptz NOT NULL,
  obligation_count integer NOT NULL CHECK (obligation_count > 0),
  shortfalls jsonb NOT NULL,
  UNIQUE (tenant_id, id), UNIQUE (tenant_id, revision_id),
  FOREIGN KEY (tenant_id, revision_id) REFERENCES sales_ord.order_payment_plan_revision(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_payment_plan_settlement_ack_order
  ON sales_ord.order_payment_plan_settlement_ack(tenant_id, sales_order_id, acknowledged_at DESC);

ALTER TABLE sales_ord.order_payment_plan_settlement_ack ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_payment_plan_settlement_ack FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_payment_plan_settlement_ack
 USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
 WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
 GRANT SELECT, INSERT ON sales_ord.order_payment_plan_settlement_ack TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
 GRANT SELECT, INSERT, DELETE ON sales_ord.order_payment_plan_settlement_ack TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
