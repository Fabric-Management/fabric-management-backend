-- Sprint 9: Composite indexes for Specification-based filtering.
-- Without these, dynamic WHERE clauses trigger sequential scans on the work order table.

CREATE INDEX IF NOT EXISTS idx_wo_tenant_status
    ON production.prod_work_order (tenant_id, status)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_wo_tenant_partner
    ON production.prod_work_order (tenant_id, trading_partner_id)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_wo_tenant_deadline
    ON production.prod_work_order (tenant_id, deadline)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_wo_tenant_sales_order
    ON production.prod_work_order (tenant_id, sales_order_id)
    WHERE is_active = true;

-- workOrderNumber already has a unique constraint but adding partial index
-- for the common tenant + active filter combination used by searchText
CREATE INDEX IF NOT EXISTS idx_wo_tenant_wonumber
    ON production.prod_work_order (tenant_id, work_order_number)
    WHERE is_active = true;
