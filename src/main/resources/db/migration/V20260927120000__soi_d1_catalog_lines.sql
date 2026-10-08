-- SOI-D1: catalogue order lines. Every line names a product; the free-text description is an
-- optional note. Clean-install assumption (SOI KDB / IK-01): no backfill for product-less rows —
-- a development database that still holds them stops here and must be rebuilt.
SET LOCAL lock_timeout = '5s';

ALTER TABLE sales_ord.sales_order_line
    ALTER COLUMN product_id SET NOT NULL;

ALTER TABLE sales_ord.sales_order_line
    ADD COLUMN IF NOT EXISTS color_id UUID,
    ADD COLUMN IF NOT EXISTS finished_width NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS finished_width_unit VARCHAR(10),
    ADD COLUMN IF NOT EXISTS requested_delivery_date DATE,
    ADD COLUMN IF NOT EXISTS initial_requested_qty NUMERIC(15, 3),
    ADD COLUMN IF NOT EXISTS single_lot_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS shipment_preference VARCHAR(20) NOT NULL DEFAULT 'AS_READY';

UPDATE sales_ord.sales_order_line
    SET initial_requested_qty = requested_qty
    WHERE initial_requested_qty IS NULL;
ALTER TABLE sales_ord.sales_order_line
    ALTER COLUMN initial_requested_qty SET NOT NULL;

ALTER TABLE sales_ord.sales_order_line
    ADD CONSTRAINT chk_sales_line_finished_width CHECK (
        (finished_width IS NULL AND finished_width_unit IS NULL)
        OR (finished_width IS NOT NULL AND finished_width > 0
            AND finished_width_unit IN ('CM', 'IN'))
    ),
    ADD CONSTRAINT chk_sales_line_initial_qty CHECK (initial_requested_qty > 0),
    ADD CONSTRAINT chk_sales_line_shipment_preference CHECK (
        shipment_preference IN ('AS_READY', 'WHEN_COMPLETE'));

-- One active distribution per product + colour + finished width + unit + delivery date (SOI TK-2).
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_line_distribution
    ON sales_ord.sales_order_line (
        tenant_id,
        sales_order_id,
        product_id,
        COALESCE(color_id, '00000000-0000-0000-0000-000000000000'::UUID),
        COALESCE(finished_width, 0),
        COALESCE(finished_width_unit, ''),
        UPPER(unit),
        COALESCE(requested_delivery_date, DATE '0001-01-01'))
    WHERE is_active = TRUE;

COMMENT ON COLUMN sales_ord.sales_order_line.product_desc IS
    'Optional line note. Never a substitute for product_id (SOI K02).';
COMMENT ON COLUMN sales_ord.sales_order_line.initial_requested_qty IS
    'Quantity first requested by the customer; immutable (SOI K08).';
COMMENT ON COLUMN sales_ord.sales_order_line.shipment_preference IS
    'How the distribution may ship once ready: AS_READY (default) or WHEN_COMPLETE (LINE-PREFERENCES-1).';

-- Customer-specific catalogue visibility (SOI K17 / IK-06).
ALTER TABLE sales.sales_product
    ADD COLUMN IF NOT EXISTS customer_id UUID;

CREATE INDEX IF NOT EXISTS idx_sales_product_customer
    ON sales.sales_product (tenant_id, product_id, customer_id)
    WHERE is_active = TRUE;

COMMENT ON COLUMN sales.sales_product.customer_id IS
    'Trading partner that may see this catalogue entry; NULL = every customer (SOI K17).';

-- Product sales definition: finished widths and extra sales units (SOI R05/R06, IK-05).
CREATE TABLE IF NOT EXISTS production.prod_product_finished_width (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    product_id UUID NOT NULL,
    width_value NUMERIC(8, 2) NOT NULL CHECK (width_value > 0),
    width_unit VARCHAR(10) NOT NULL CHECK (width_unit IN ('CM', 'IN'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_prod_product_finished_width
    ON production.prod_product_finished_width (tenant_id, product_id, width_value, width_unit)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS production.prod_product_sales_unit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    product_id UUID NOT NULL,
    unit VARCHAR(20) NOT NULL CHECK (length(trim(unit)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_prod_product_sales_unit
    ON production.prod_product_sales_unit (tenant_id, product_id, UPPER(unit))
    WHERE is_active = TRUE;

ALTER TABLE production.prod_product_finished_width ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_product_finished_width FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.prod_product_finished_width FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

ALTER TABLE production.prod_product_sales_unit ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_product_sales_unit FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.prod_product_sales_unit FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE ON production.prod_product_finished_width,
            production.prod_product_sales_unit TO fabric_app;
        REVOKE DELETE ON production.prod_product_finished_width,
            production.prod_product_sales_unit FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON production.prod_product_finished_width,
            production.prod_product_sales_unit TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE production.prod_product_finished_width IS
    'Finished widths a product is sold in; an order line may only name one of these (SOI R05).';
COMMENT ON TABLE production.prod_product_sales_unit IS
    'Sales units allowed besides the product base unit; no conversion between them (SOI R06).';
