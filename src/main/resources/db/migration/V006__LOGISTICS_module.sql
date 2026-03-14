-- ============================================
-- MODULE: LOGISTICS
-- Kaynak: V042, V044
-- Not: V042 sales_order tablosu V066 ile "order" şemasına taşındı → V005'te. Burada sadece shipment.
-- ============================================

CREATE SCHEMA IF NOT EXISTS logistics;

-- ============================================================================
-- logistics_shipment
-- ============================================================================
CREATE TABLE IF NOT EXISTS logistics.logistics_shipment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    trading_partner_id UUID NOT NULL,
    shipment_number VARCHAR(50) NOT NULL,
    order_reference VARCHAR(100),
    shipment_type VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    carrier_name VARCHAR(100),
    carrier_code VARCHAR(50),
    tracking_number VARCHAR(100),
    tracking_url VARCHAR(500),
    ship_date DATE,
    estimated_delivery_date DATE,
    actual_delivery_date DATE,
    picked_up_at TIMESTAMP,
    delivered_at TIMESTAMP,
    origin_address VARCHAR(500),
    destination_address VARCHAR(500) NOT NULL,
    total_weight NUMERIC(10,3),
    weight_unit VARCHAR(10) DEFAULT 'KG',
    package_count INTEGER,
    shipping_cost NUMERIC(19,4),
    currency VARCHAR(3) DEFAULT 'TRY',
    delivery_proof VARCHAR(500),
    recipient_name VARCHAR(100),
    notes TEXT,
    metadata JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ship_trading_partner FOREIGN KEY (trading_partner_id) REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT,
    CONSTRAINT uk_ship_tenant_shipment_number UNIQUE (tenant_id, shipment_number)
);

CREATE INDEX idx_ship_tenant ON logistics.logistics_shipment(tenant_id);
CREATE INDEX idx_ship_trading_partner ON logistics.logistics_shipment(trading_partner_id);
CREATE INDEX idx_ship_status ON logistics.logistics_shipment(status);
CREATE INDEX idx_ship_tracking ON logistics.logistics_shipment(tracking_number) WHERE tracking_number IS NOT NULL;
CREATE INDEX idx_ship_ship_date ON logistics.logistics_shipment(ship_date);

-- [LOGISTICS] module migration tamamlandı.
