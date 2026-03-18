-- =========================================================================
-- MODULE: IWM (Faz 10.2) - Stock Reservation
-- =========================================================================

CREATE TABLE IF NOT EXISTS iwm.stock_reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    sales_order_line_id UUID NOT NULL,
    location_id UUID NOT NULL,
    material_id UUID NOT NULL,
    lot_number VARCHAR(100) NOT NULL,
    goods_receipt_item_id UUID,
    qty_reserved DECIMAL(15,3) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_stock_res_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX idx_stock_res_sales_line ON iwm.stock_reservation(sales_order_line_id);
CREATE INDEX idx_stock_res_location ON iwm.stock_reservation(location_id);
CREATE INDEX idx_stock_res_material ON iwm.stock_reservation(material_id);
CREATE INDEX idx_stock_res_status ON iwm.stock_reservation(status);
