-- Business selection metadata; actual temporary ownership uses common_infrastructure.live_edit_lease.
ALTER TABLE sales_ord.sales_order_line ADD COLUMN customer_request_qty numeric(15,3);
UPDATE sales_ord.sales_order_line SET customer_request_qty = initial_requested_qty;
-- DRAFT-HOLD-BE-2: preserve metre-only entry and unknown historical customer decisions.
ALTER TABLE sales_ord.sales_order_line
  ADD COLUMN quantity_policy varchar(20) NOT NULL DEFAULT 'UNRECORDED',
  ADD COLUMN quantity_policy_recorded_by uuid,
  ADD COLUMN quantity_policy_recorded_at timestamptz,
  ADD COLUMN quantity_policy_reason varchar(500),
  ADD CONSTRAINT ck_order_line_quantity_policy CHECK (quantity_policy IN ('UNRECORDED','BY_AGREEMENT','EXACT')),
  ADD CONSTRAINT ck_order_line_exact_quantity CHECK (quantity_policy <> 'EXACT' OR (customer_request_qty IS NOT NULL AND requested_qty = customer_request_qty));
ALTER TABLE sales.quote_line
  ADD COLUMN customer_request_qty numeric(15,3),
  ADD COLUMN initial_requested_qty numeric(15,3),
  ADD COLUMN finished_width numeric(8,2),
  ADD COLUMN finished_width_unit varchar(10),
  ADD COLUMN single_lot_required boolean NOT NULL DEFAULT false,
  ADD COLUMN tolerance_up_pct numeric(5,2),
  ADD COLUMN tolerance_down_pct numeric(5,2),
  ADD COLUMN quantity_policy varchar(20) NOT NULL DEFAULT 'UNRECORDED',
  ADD COLUMN quantity_policy_recorded_by uuid,
  ADD COLUMN quantity_policy_recorded_at timestamptz,
  ADD COLUMN quantity_policy_reason varchar(500),
  ADD CONSTRAINT ck_quote_quantity_policy CHECK (quantity_policy IN ('UNRECORDED','BY_AGREEMENT','EXACT')),
  ADD CONSTRAINT ck_quote_exact_quantity CHECK (quantity_policy <> 'EXACT' OR (customer_request_qty IS NOT NULL AND requested_qty = customer_request_qty));
CREATE TABLE IF NOT EXISTS sales_ord.draft_stock_selection (
    id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
    tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
    deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
    form_instance_id uuid NOT NULL, client_line_id uuid NOT NULL, product_id uuid NOT NULL,
    actor_id uuid NOT NULL, session_id uuid,
    state varchar(255) NOT NULL CHECK (state IN ('OPEN','HELD','RELEASED','TRANSFERRED','CONVERTED','SUPERSEDED')),
    request jsonb, option jsonb, tokens jsonb NOT NULL DEFAULT '[]', expires_at timestamptz,
    source_quote_id uuid, source_quote_line_id uuid,
    target_order_id uuid, target_line_id uuid,
    target_quote_id uuid, target_quote_line_id uuid,
    transferred_quote_id uuid, transferred_quote_line_id uuid,
    sales_order_id uuid, sales_order_line_id uuid, terms_fingerprint varchar(255), order_version bigint,
    CONSTRAINT ck_stock_selection_target CHECK (
      (target_order_id IS NULL AND target_line_id IS NULL AND (target_quote_line_id IS NULL OR target_quote_id IS NOT NULL))
      OR (target_order_id IS NOT NULL AND target_line_id IS NOT NULL AND target_quote_id IS NULL AND target_quote_line_id IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_draft_stock_form_line ON sales_ord.draft_stock_selection(tenant_id, form_instance_id, client_line_id, actor_id)
  WHERE state IN ('OPEN', 'HELD') AND is_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_selection_active_line ON sales_ord.draft_stock_selection(tenant_id, sales_order_line_id)
  WHERE state = 'TRANSFERRED' AND is_active;
CREATE INDEX IF NOT EXISTS idx_stock_selection_product ON sales_ord.draft_stock_selection(tenant_id, product_id, state);
CREATE INDEX IF NOT EXISTS idx_stock_selection_session_owner ON sales_ord.draft_stock_selection(tenant_id, session_id);
CREATE INDEX IF NOT EXISTS idx_stock_selection_order_line_target ON sales_ord.draft_stock_selection(tenant_id, target_line_id, state);
CREATE INDEX IF NOT EXISTS idx_stock_selection_quote_line_target ON sales_ord.draft_stock_selection(tenant_id, target_quote_line_id, state);
CREATE INDEX IF NOT EXISTS idx_stock_selection_quote_target ON sales_ord.draft_stock_selection(tenant_id, target_quote_id, target_quote_line_id);
CREATE INDEX IF NOT EXISTS idx_stock_selection_quote_transfer ON sales_ord.draft_stock_selection(tenant_id, transferred_quote_line_id, state);
CREATE TABLE IF NOT EXISTS sales_ord.draft_stock_selection_operation (
    id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
    tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
    deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
    operation_id uuid NOT NULL, selection_id uuid NOT NULL, actor_id uuid NOT NULL,
    request_hash varchar(64) NOT NULL, result jsonb NOT NULL,
    CONSTRAINT uq_draft_stock_operation UNIQUE (tenant_id, operation_id)
);
ALTER TABLE sales_ord.draft_stock_selection ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.draft_stock_selection FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.draft_stock_selection
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.draft_stock_selection TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.draft_stock_selection TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE sales_ord.draft_stock_selection_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.draft_stock_selection_operation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.draft_stock_selection_operation
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.draft_stock_selection_operation TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.draft_stock_selection_operation TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

-- DRAFT-HOLD-BE-2: idempotency receipts for quote stock commands and order-line quantity decisions.
CREATE TABLE IF NOT EXISTS sales.quote_stock_operation (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE, tenant_id uuid NOT NULL,
  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  quote_id uuid NOT NULL, actor_id uuid NOT NULL, operation_id uuid NOT NULL,
  request_hash varchar(64) NOT NULL, result jsonb NOT NULL,
  CONSTRAINT uq_quote_stock_operation UNIQUE(tenant_id, operation_id)
);
CREATE INDEX IF NOT EXISTS idx_quote_stock_operation_quote ON sales.quote_stock_operation(tenant_id, quote_id);
ALTER TABLE sales.quote_stock_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.quote_stock_operation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales.quote_stock_operation
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE TABLE IF NOT EXISTS sales_ord.quantity_policy_operation (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE, tenant_id uuid NOT NULL,
  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  order_id uuid NOT NULL, line_id uuid NOT NULL, actor_id uuid NOT NULL, operation_id uuid NOT NULL,
  expected_version bigint NOT NULL, order_version bigint NOT NULL, reason varchar(500) NOT NULL, result jsonb NOT NULL,
  CONSTRAINT uq_quantity_policy_operation UNIQUE(tenant_id, operation_id)
);
CREATE INDEX IF NOT EXISTS idx_quantity_policy_order ON sales_ord.quantity_policy_operation(tenant_id, order_id);
ALTER TABLE sales_ord.quantity_policy_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.quantity_policy_operation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.quantity_policy_operation
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON sales.quote_stock_operation, sales_ord.quantity_policy_operation TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON sales.quote_stock_operation, sales_ord.quantity_policy_operation TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
