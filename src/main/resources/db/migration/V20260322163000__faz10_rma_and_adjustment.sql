-- =========================================================================
-- MODULE: IWM (Faz 10.5) - RMA & Stock Adjustment
-- =========================================================================

-- 1. RMA
CREATE TABLE IF NOT EXISTS iwm.rma (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    rma_number VARCHAR(100) NOT NULL,
    trading_partner_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by UUID,
    notes TEXT,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_rma_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_rma_number UNIQUE (tenant_id, rma_number)
);

CREATE INDEX idx_rma_partner ON iwm.rma(trading_partner_id);
CREATE INDEX idx_rma_status ON iwm.rma(status);

-- 2. RmaLine
CREATE TABLE IF NOT EXISTS iwm.rma_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    rma_id UUID NOT NULL,
    sales_order_line_id UUID NOT NULL,
    material_id UUID NOT NULL,
    lot_number VARCHAR(100) NOT NULL,
    defect_category VARCHAR(50) NOT NULL,
    qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    destination_location_id UUID,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_rma_line_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX idx_rma_line_rma ON iwm.rma_line(rma_id);
CREATE INDEX idx_rma_line_material ON iwm.rma_line(material_id);

-- 3. StockAdjustmentRequest
CREATE TABLE IF NOT EXISTS iwm.stock_adjustment_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    request_number VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL,
    material_id UUID NOT NULL,
    lot_number VARCHAR(100) NOT NULL,
    qty_adjustment DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by UUID,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_stock_adj_req_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_stock_adj_req_number UNIQUE (tenant_id, request_number)
);

CREATE INDEX idx_stock_adj_loc ON iwm.stock_adjustment_request(location_id);
CREATE INDEX idx_stock_adj_status ON iwm.stock_adjustment_request(status);
