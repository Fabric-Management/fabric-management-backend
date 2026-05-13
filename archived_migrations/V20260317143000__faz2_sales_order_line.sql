-- SalesOrderLine + SalesOrder Faz 2 columns: V20260317143000

-- Add Faz 2 columns to existing sales_order table
ALTER TABLE "order".sales_order
    ADD COLUMN IF NOT EXISTS module_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS deadline DATE,
    ADD COLUMN IF NOT EXISTS quote_id UUID,
    ADD COLUMN IF NOT EXISTS sample_request_id UUID;

CREATE INDEX IF NOT EXISTS idx_so_module_type
    ON "order".sales_order(module_type)
    WHERE module_type IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_so_deadline
    ON "order".sales_order(deadline)
    WHERE deadline IS NOT NULL;

-- SalesOrderLine table
CREATE TABLE IF NOT EXISTS "order".sales_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    sales_order_id UUID NOT NULL,
    product_id UUID,
    product_desc TEXT,
    requested_qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18,4),
    currency VARCHAR(3),
    module_type VARCHAR(20)
        CONSTRAINT ck_sol_module_type CHECK (module_type IN (
            'FIBER', 'YARN', 'FABRIC', 'DYE_FINISHING'
        )),
    module_specs JSONB,
    line_status VARCHAR(25) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT ck_sol_line_status CHECK (line_status IN (
            'PENDING', 'RECIPE_ASSIGNED', 'IN_PRODUCTION',
            'COMPLETED', 'IN_WAREHOUSE', 'SHIPPED', 'CANCELLED'
        )),
    recipe_id UUID,

    -- Ensure at least one of product_id or product_desc is non-null
    CONSTRAINT ck_sol_product_or_desc CHECK (
        product_id IS NOT NULL OR product_desc IS NOT NULL
    )
);

-- ── sales_order_line indexes ──────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_sol_sales_order_id
    ON "order".sales_order_line(sales_order_id);

CREATE INDEX IF NOT EXISTS idx_sol_tenant_id
    ON "order".sales_order_line(tenant_id);

CREATE INDEX IF NOT EXISTS idx_sol_product_id
    ON "order".sales_order_line(product_id)
    WHERE product_id IS NOT NULL;

-- Status-based querying (RuleEngine pending assignments, IWM hooks)
CREATE INDEX IF NOT EXISTS idx_sol_line_status
    ON "order".sales_order_line(line_status)
    WHERE is_active = true;

-- recipe lookup (WorkOrder ↔ SalesOrderLine reconciliation)
CREATE INDEX IF NOT EXISTS idx_sol_recipe_id
    ON "order".sales_order_line(recipe_id)
    WHERE recipe_id IS NOT NULL;
