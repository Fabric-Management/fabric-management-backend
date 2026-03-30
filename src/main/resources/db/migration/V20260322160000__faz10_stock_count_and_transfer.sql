-- =========================================================================
-- MODULE: IWM (Faz 10.4) - Stock Count & Stock Transfer
-- =========================================================================

-- 1. StockCount
CREATE TABLE IF NOT EXISTS iwm.stock_count (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    count_number VARCHAR(100) NOT NULL,
    count_type VARCHAR(30) NOT NULL,
    location_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    planned_at DATE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_stock_count_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_stock_count_number UNIQUE (tenant_id, count_number)
);

CREATE INDEX idx_stock_count_location ON iwm.stock_count(location_id);
CREATE INDEX idx_stock_count_status ON iwm.stock_count(status);

-- 2. StockCountLine
CREATE TABLE IF NOT EXISTS iwm.stock_count_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    stock_count_id UUID NOT NULL,
    material_id UUID NOT NULL,
    lot_number VARCHAR(100) NOT NULL,
    goods_receipt_item_id UUID,
    barcode VARCHAR(100),
    system_qty DECIMAL(15,3) NOT NULL,
    counted_qty DECIMAL(15,3),
    variance DECIMAL(15,3),
    variance_reason TEXT,
    entry_method VARCHAR(30) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_stock_count_line_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX idx_count_line_count ON iwm.stock_count_line(stock_count_id);

-- 3. StockCountAssignee
CREATE TABLE IF NOT EXISTS iwm.stock_count_assignee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    stock_count_id UUID NOT NULL,
    user_id UUID NOT NULL,
    assigned_zone VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_count_assignee_count ON iwm.stock_count_assignee(stock_count_id);

-- 4. StockCountTolerance
CREATE TABLE IF NOT EXISTS iwm.stock_count_tolerance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    module_type VARCHAR(50) NOT NULL,
    auto_adjust_threshold DECIMAL(5,2) NOT NULL,
    requires_manager_approval DECIMAL(5,2) NOT NULL,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_stock_count_tolerance_tenant_uid UNIQUE (tenant_id, uid)
);

-- 5. StockTransfer
CREATE TABLE IF NOT EXISTS iwm.stock_transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    transfer_number VARCHAR(100) NOT NULL,
    transfer_type VARCHAR(30) NOT NULL,
    from_location_id UUID NOT NULL,
    to_location_id UUID NOT NULL,
    material_id UUID NOT NULL,
    lot_number VARCHAR(100) NOT NULL,
    qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source_type VARCHAR(30),
    source_id UUID,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    vehicle_info VARCHAR(255),
    notes TEXT,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_stock_transfer_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_stock_transfer_number UNIQUE (tenant_id, transfer_number)
);

CREATE INDEX idx_transfer_from_loc ON iwm.stock_transfer(from_location_id);
CREATE INDEX idx_transfer_to_loc ON iwm.stock_transfer(to_location_id);
CREATE INDEX idx_transfer_status ON iwm.stock_transfer(status);
