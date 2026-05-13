-- Create WorkOrder tables: V20260317131000__create_work_order_tables.sql

CREATE TABLE IF NOT EXISTS production.prod_work_order (
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

    -- Unique business key: enforced at DB level to prevent race-condition duplicates
    work_order_number VARCHAR(100) NOT NULL UNIQUE,
    recipe_id UUID,
    trading_partner_id UUID,
    sales_order_line_id UUID,
    fulfillment_type VARCHAR(20) NOT NULL,
    fulfillment_id UUID,
    planned_qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_cost DECIMAL(15,3),
    currency VARCHAR(3),
    planned_cost DECIMAL(15,3),
    planned_cost_currency VARCHAR(3),
    status VARCHAR(20) NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    attachments JSONB,

    -- Supplier snapshot (captured at creation, immutable)
    supplier_certification_code VARCHAR(100),
    supplier_license_no VARCHAR(100),
    supplier_license_valid_until DATE
);

CREATE TABLE IF NOT EXISTS production.prod_work_order_assignee (
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

    work_order_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    department_id UUID,
    user_id UUID,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ── prod_work_order indexes ───────────────────────────────────────────────────

-- Tenant-scoped listing (every query starts here)
CREATE INDEX IF NOT EXISTS idx_work_order_tenant_id
    ON production.prod_work_order(tenant_id);

-- Status dashboard queries (e.g. all IN_PROGRESS orders for a tenant)
CREATE INDEX IF NOT EXISTS idx_work_order_status_tenant
    ON production.prod_work_order(tenant_id, status)
    WHERE is_active = true;

-- Business number lookup (traceability, invoices, shipping docs)
CREATE INDEX IF NOT EXISTS idx_work_order_number
    ON production.prod_work_order(work_order_number);

-- Recipe → WorkOrder reverse lookup
CREATE INDEX IF NOT EXISTS idx_work_order_recipe
    ON production.prod_work_order(recipe_id)
    WHERE recipe_id IS NOT NULL;

-- Supplier / trading-partner reporting
CREATE INDEX IF NOT EXISTS idx_work_order_supplier
    ON production.prod_work_order(trading_partner_id)
    WHERE trading_partner_id IS NOT NULL;

-- Sales order line → related work orders
CREATE INDEX IF NOT EXISTS idx_work_order_sales_line
    ON production.prod_work_order(sales_order_line_id)
    WHERE sales_order_line_id IS NOT NULL;

-- Deadline / scheduling queries
CREATE INDEX IF NOT EXISTS idx_work_order_deadline
    ON production.prod_work_order(deadline)
    WHERE deadline IS NOT NULL AND is_active = true;

-- ── prod_work_order_assignee indexes ─────────────────────────────────────────

-- Primary: all assignees for a given work order
CREATE INDEX IF NOT EXISTS idx_wo_assignee_wo_id
    ON production.prod_work_order_assignee(work_order_id);

-- Reverse: all work orders assigned to a user
CREATE INDEX IF NOT EXISTS idx_wo_assignee_user_id
    ON production.prod_work_order_assignee(user_id)
    WHERE user_id IS NOT NULL;

-- Reverse: all work orders assigned to a department
CREATE INDEX IF NOT EXISTS idx_wo_assignee_dept_id
    ON production.prod_work_order_assignee(department_id)
    WHERE department_id IS NOT NULL;

-- Unique: one user cannot have the same role twice on the same work order
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_wo_assignee
    ON production.prod_work_order_assignee(work_order_id, role, user_id)
    WHERE is_active = true AND deleted_at IS NULL AND user_id IS NOT NULL;
