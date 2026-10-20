-- Business selection metadata; actual temporary ownership uses common_infrastructure.live_edit_lease.
ALTER TABLE sales_ord.sales_order_line ADD COLUMN customer_request_qty numeric(15,3);
UPDATE sales_ord.sales_order_line SET customer_request_qty = initial_requested_qty;
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
    sales_order_id uuid, sales_order_line_id uuid, terms_fingerprint varchar(255), order_version bigint
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_draft_stock_form_line ON sales_ord.draft_stock_selection(tenant_id, form_instance_id, client_line_id, actor_id)
  WHERE state IN ('OPEN', 'HELD') AND is_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_selection_active_line ON sales_ord.draft_stock_selection(tenant_id, sales_order_line_id)
  WHERE state = 'TRANSFERRED' AND is_active;
CREATE INDEX IF NOT EXISTS idx_stock_selection_product ON sales_ord.draft_stock_selection(tenant_id, product_id, state);
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
