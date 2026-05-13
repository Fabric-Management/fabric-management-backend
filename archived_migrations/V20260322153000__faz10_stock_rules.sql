-- =========================================================================
-- MODULE: IWM (Faz 10.3) - Stock Rules
-- =========================================================================

-- 1. MinStockRule
CREATE TABLE IF NOT EXISTS iwm.min_stock_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    min_qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    last_alert_at TIMESTAMP WITH TIME ZONE,
    alert_cooldown_hours INTEGER NOT NULL DEFAULT 24,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_min_stock_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX idx_min_stock_location ON iwm.min_stock_rule(location_id);
CREATE INDEX idx_min_stock_product ON iwm.min_stock_rule(product_id);

-- 2. LotEndRule
CREATE TABLE IF NOT EXISTS iwm.lot_end_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    module_type VARCHAR(50) NOT NULL,
    threshold_qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    show_warning BOOLEAN NOT NULL DEFAULT TRUE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_lot_end_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX idx_lot_end_module ON iwm.lot_end_rule(module_type);

-- 3. ReturnRateRule
CREATE TABLE IF NOT EXISTS iwm.return_rate_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    trading_partner_id UUID NOT NULL,
    threshold_rate DECIMAL(5,2) NOT NULL,
    window_days INTEGER NOT NULL DEFAULT 90,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_return_rate_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX idx_return_rate_partner ON iwm.return_rate_rule(trading_partner_id);
