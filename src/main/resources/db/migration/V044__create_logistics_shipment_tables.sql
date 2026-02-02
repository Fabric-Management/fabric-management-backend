-- ═══════════════════════════════════════════════════════════════════════════
-- V044: Create Logistics Shipment Tables
-- ═══════════════════════════════════════════════════════════════════════════
-- Creates Shipment table with TradingPartner FK (Faz 1.5 pattern).
--
-- Key Features:
-- - Uses trading_partner_id for origin/destination partner
-- - Supports inbound and outbound shipments
-- - Tracks carrier, tracking info, and delivery status
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 1: Ensure logistics schema exists (created in V042)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE SCHEMA IF NOT EXISTS logistics;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 2: Create Shipment table
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS logistics.logistics_shipment (
    -- BaseEntity fields
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- TradingPartner FK
    trading_partner_id UUID NOT NULL,
    
    -- Shipment identification
    shipment_number VARCHAR(50) NOT NULL,
    order_reference VARCHAR(100),
    shipment_type VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    
    -- Carrier & Tracking
    carrier_name VARCHAR(100),
    carrier_code VARCHAR(50),
    tracking_number VARCHAR(100),
    tracking_url VARCHAR(500),
    
    -- Dates
    ship_date DATE,
    estimated_delivery_date DATE,
    actual_delivery_date DATE,
    picked_up_at TIMESTAMP,
    delivered_at TIMESTAMP,
    
    -- Addresses
    origin_address VARCHAR(500),
    destination_address VARCHAR(500) NOT NULL,
    
    -- Package info
    total_weight NUMERIC(10,3),
    weight_unit VARCHAR(10) DEFAULT 'KG',
    package_count INTEGER,
    
    -- Costs
    shipping_cost NUMERIC(19,4),
    currency VARCHAR(3) DEFAULT 'TRY',
    
    -- Delivery info
    delivery_proof VARCHAR(500),
    recipient_name VARCHAR(100),
    
    -- Metadata
    notes TEXT,
    metadata JSONB,
    
    -- BaseEntity audit fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Foreign keys
    CONSTRAINT fk_ship_trading_partner FOREIGN KEY (trading_partner_id)
        REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT,
    
    -- Unique constraints
    CONSTRAINT uk_ship_tenant_shipment_number UNIQUE (tenant_id, shipment_number)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 3: Create indexes
-- ═══════════════════════════════════════════════════════════════════════════

-- Primary lookup indexes
CREATE INDEX IF NOT EXISTS idx_ship_tenant ON logistics.logistics_shipment(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ship_trading_partner ON logistics.logistics_shipment(trading_partner_id);

-- Status and type indexes
CREATE INDEX IF NOT EXISTS idx_ship_status ON logistics.logistics_shipment(status);
CREATE INDEX IF NOT EXISTS idx_ship_type ON logistics.logistics_shipment(shipment_type);

-- Tracking lookup
CREATE INDEX IF NOT EXISTS idx_ship_tracking ON logistics.logistics_shipment(tracking_number)
    WHERE tracking_number IS NOT NULL;

-- Date indexes
CREATE INDEX IF NOT EXISTS idx_ship_ship_date ON logistics.logistics_shipment(ship_date);
CREATE INDEX IF NOT EXISTS idx_ship_est_delivery ON logistics.logistics_shipment(estimated_delivery_date);

-- Composite indexes
CREATE INDEX IF NOT EXISTS idx_ship_tenant_status ON logistics.logistics_shipment(tenant_id, status)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_ship_tenant_partner ON logistics.logistics_shipment(tenant_id, trading_partner_id)
    WHERE is_active = TRUE;

-- In-transit shipments optimization
CREATE INDEX IF NOT EXISTS idx_ship_in_transit ON logistics.logistics_shipment(tenant_id, estimated_delivery_date ASC)
    WHERE is_active = TRUE AND status IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY');

-- Pending shipments optimization
CREATE INDEX IF NOT EXISTS idx_ship_pending ON logistics.logistics_shipment(tenant_id, ship_date ASC)
    WHERE is_active = TRUE AND status IN ('PENDING', 'PREPARING', 'READY');

-- Carrier queries
CREATE INDEX IF NOT EXISTS idx_ship_carrier ON logistics.logistics_shipment(carrier_code)
    WHERE carrier_code IS NOT NULL;

-- JSONB index
CREATE INDEX IF NOT EXISTS idx_ship_metadata ON logistics.logistics_shipment USING GIN (metadata)
    WHERE metadata IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 4: Comments
-- ═══════════════════════════════════════════════════════════════════════════
COMMENT ON TABLE logistics.logistics_shipment IS 
'Shipments with TradingPartner integration. Uses trading_partner_id for origin/destination reference.';

COMMENT ON COLUMN logistics.logistics_shipment.trading_partner_id IS 
'FK to TradingPartner. For OUTBOUND: customer/destination. For INBOUND: supplier/origin.';

COMMENT ON COLUMN logistics.logistics_shipment.shipment_type IS 
'OUTBOUND, INBOUND, RETURN_INBOUND, RETURN_OUTBOUND, TRANSFER';

COMMENT ON COLUMN logistics.logistics_shipment.status IS 
'PENDING, PREPARING, READY, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, RETURNED, CANCELLED';

-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK
-- ═══════════════════════════════════════════════════════════════════════════
-- DROP TABLE IF EXISTS logistics.logistics_shipment CASCADE;
