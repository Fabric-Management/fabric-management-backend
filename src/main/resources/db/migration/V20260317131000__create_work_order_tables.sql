-- Create WorkOrder tables: V20250317131000__create_work_order_tables.sql

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
    
    work_order_number VARCHAR(100) NOT NULL,
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

-- Indexes for querying
CREATE INDEX IF NOT EXISTS idx_work_order_number ON production.prod_work_order(work_order_number);
CREATE INDEX IF NOT EXISTS idx_work_order_recipe ON production.prod_work_order(recipe_id);
CREATE INDEX IF NOT EXISTS idx_work_order_supplier ON production.prod_work_order(trading_partner_id);
CREATE INDEX IF NOT EXISTS idx_work_order_sales_line ON production.prod_work_order(sales_order_line_id);

CREATE INDEX IF NOT EXISTS idx_wo_assignee_wo_id ON production.prod_work_order_assignee(work_order_id);
CREATE INDEX IF NOT EXISTS idx_wo_assignee_user_id ON production.prod_work_order_assignee(user_id);

-- Constraint for uniqueness in roles per user
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_wo_assignee 
    ON production.prod_work_order_assignee(work_order_id, role, user_id) 
    WHERE is_active = true AND deleted_at IS NULL AND user_id IS NOT NULL;
