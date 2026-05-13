-- =========================================================================
-- LOGISTICS: Shipment Line and Shipment Line Batch tables
-- =========================================================================

CREATE TABLE IF NOT EXISTS logistics.logistics_shipment_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    shipment_id UUID NOT NULL REFERENCES logistics.logistics_shipment(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    sales_order_line_id UUID NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_shp_line_shp_id ON logistics.logistics_shipment_line(shipment_id);
CREATE INDEX idx_shp_line_sol_id ON logistics.logistics_shipment_line(sales_order_line_id);

CREATE TABLE IF NOT EXISTS logistics.logistics_shipment_line_batch (
    shipment_line_id UUID NOT NULL REFERENCES logistics.logistics_shipment_line(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE,
    quality_grade_snapshot VARCHAR(20),
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    PRIMARY KEY (shipment_line_id, batch_id)
);

CREATE INDEX idx_shp_line_batch_batch_id ON logistics.logistics_shipment_line_batch(batch_id);
