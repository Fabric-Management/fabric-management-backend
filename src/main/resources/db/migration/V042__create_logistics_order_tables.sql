-- ═══════════════════════════════════════════════════════════════════════════
-- V042: Create Logistics Order Tables
-- ═══════════════════════════════════════════════════════════════════════════
-- Creates SalesOrder table with TradingPartner FK (Faz 1.5 pattern).
--
-- Key Features:
-- - Uses trading_partner_id as primary customer FK (NOT company_id)
-- - Follows BaseEntity pattern (tenant_id, uid, audit fields)
-- - Supports order lifecycle (DRAFT → CONFIRMED → SHIPPED → DELIVERED)
--
-- TradingPartner Integration:
-- - FK to common_company.common_trading_partner(id)
-- - No legacy company_id column (clean implementation)
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 1: Create logistics schema if not exists
-- ═══════════════════════════════════════════════════════════════════════════
CREATE SCHEMA IF NOT EXISTS logistics;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 2: Create SalesOrder table
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS logistics.logistics_sales_order (
    -- BaseEntity fields
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- TradingPartner FK (Faz 1.5 - primary customer reference)
    trading_partner_id UUID NOT NULL,
    
    -- Order identification
    order_number VARCHAR(50) NOT NULL,
    customer_reference VARCHAR(100),
    order_type VARCHAR(20) NOT NULL DEFAULT 'SALES',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    
    -- Dates
    order_date DATE NOT NULL,
    requested_delivery_date DATE,
    promised_delivery_date DATE,
    actual_delivery_date DATE,
    
    -- Financial
    total_amount NUMERIC(19,4),
    tax_amount NUMERIC(19,4),
    discount_amount NUMERIC(19,4),
    currency VARCHAR(3) DEFAULT 'TRY',
    
    -- Shipping
    shipping_address VARCHAR(500),
    billing_address VARCHAR(500),
    shipping_method VARCHAR(50),
    
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
    CONSTRAINT fk_so_trading_partner FOREIGN KEY (trading_partner_id)
        REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT,
    
    -- Unique constraints
    CONSTRAINT uk_so_tenant_order_number UNIQUE (tenant_id, order_number)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 3: Create indexes
-- ═══════════════════════════════════════════════════════════════════════════

-- Primary lookup indexes
CREATE INDEX IF NOT EXISTS idx_so_tenant ON logistics.logistics_sales_order(tenant_id);
CREATE INDEX IF NOT EXISTS idx_so_trading_partner ON logistics.logistics_sales_order(trading_partner_id);

-- Status and type indexes
CREATE INDEX IF NOT EXISTS idx_so_status ON logistics.logistics_sales_order(status);
CREATE INDEX IF NOT EXISTS idx_so_order_type ON logistics.logistics_sales_order(order_type);

-- Date indexes for range queries
CREATE INDEX IF NOT EXISTS idx_so_order_date ON logistics.logistics_sales_order(order_date);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_so_tenant_status ON logistics.logistics_sales_order(tenant_id, status)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_so_tenant_partner ON logistics.logistics_sales_order(tenant_id, trading_partner_id)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_so_tenant_date ON logistics.logistics_sales_order(tenant_id, order_date DESC)
    WHERE is_active = TRUE;

-- Open orders query optimization (non-terminal status)
CREATE INDEX IF NOT EXISTS idx_so_open_orders ON logistics.logistics_sales_order(tenant_id, order_date DESC)
    WHERE is_active = TRUE AND status NOT IN ('DELIVERED', 'CANCELLED');

-- JSONB index for metadata queries
CREATE INDEX IF NOT EXISTS idx_so_metadata ON logistics.logistics_sales_order USING GIN (metadata)
    WHERE metadata IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 4: Table and column comments
-- ═══════════════════════════════════════════════════════════════════════════
COMMENT ON TABLE logistics.logistics_sales_order IS 
'Sales orders with TradingPartner integration. Uses trading_partner_id (NOT company_id) for customer reference.';

COMMENT ON COLUMN logistics.logistics_sales_order.trading_partner_id IS 
'FK to TradingPartner (customer). Primary reference for order partner - no legacy company_id column.';

COMMENT ON COLUMN logistics.logistics_sales_order.order_number IS 
'Human-readable order number. Format: SO-YYYYMMDD-XXXXX (unique per tenant).';

COMMENT ON COLUMN logistics.logistics_sales_order.status IS 
'Order lifecycle: DRAFT, CONFIRMED, IN_PROGRESS, PARTIALLY_SHIPPED, SHIPPED, DELIVERED, CANCELLED, ON_HOLD';

COMMENT ON COLUMN logistics.logistics_sales_order.metadata IS 
'JSONB for flexible order attributes: payment_terms, incoterms, special_instructions, etc.';

-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK (run manually if needed)
-- ═══════════════════════════════════════════════════════════════════════════
-- DROP TABLE IF EXISTS logistics.logistics_sales_order CASCADE;
-- DROP SCHEMA IF EXISTS logistics CASCADE;
