-- ═══════════════════════════════════════════════════════════════════════════
-- V043: Create Finance Invoice Tables
-- ═══════════════════════════════════════════════════════════════════════════
-- Creates Invoice table with TradingPartner FK (Faz 1.5 pattern).
-- Supports both AR (sales invoices) and AP (purchase invoices).
--
-- Key Features:
-- - Uses trading_partner_id as primary customer/vendor FK
-- - Follows BaseEntity pattern
-- - Supports invoice lifecycle (DRAFT → ISSUED → SENT → PAID)
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 1: Create finance schema if not exists
-- ═══════════════════════════════════════════════════════════════════════════
CREATE SCHEMA IF NOT EXISTS finance;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 2: Create Invoice table
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS finance.finance_invoice (
    -- BaseEntity fields
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- TradingPartner FK
    trading_partner_id UUID NOT NULL,
    
    -- Invoice identification
    invoice_number VARCHAR(50) NOT NULL,
    order_reference VARCHAR(100),
    external_reference VARCHAR(100),
    invoice_type VARCHAR(20) NOT NULL DEFAULT 'SALES',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    
    -- Dates
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    payment_date DATE,
    
    -- Financial
    subtotal NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) DEFAULT 0,
    discount_amount NUMERIC(19,4) DEFAULT 0,
    total_amount NUMERIC(19,4) NOT NULL,
    amount_paid NUMERIC(19,4) DEFAULT 0,
    amount_due NUMERIC(19,4),
    currency VARCHAR(3) DEFAULT 'TRY',
    tax_rate NUMERIC(5,2),
    
    -- Address
    billing_address VARCHAR(500),
    
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
    CONSTRAINT fk_inv_trading_partner FOREIGN KEY (trading_partner_id)
        REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT,
    
    -- Unique constraints
    CONSTRAINT uk_inv_tenant_invoice_number UNIQUE (tenant_id, invoice_number)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 3: Create indexes
-- ═══════════════════════════════════════════════════════════════════════════

-- Primary lookup indexes
CREATE INDEX IF NOT EXISTS idx_inv_tenant ON finance.finance_invoice(tenant_id);
CREATE INDEX IF NOT EXISTS idx_inv_trading_partner ON finance.finance_invoice(trading_partner_id);

-- Status and type indexes
CREATE INDEX IF NOT EXISTS idx_inv_status ON finance.finance_invoice(status);
CREATE INDEX IF NOT EXISTS idx_inv_type ON finance.finance_invoice(invoice_type);

-- Date indexes
CREATE INDEX IF NOT EXISTS idx_inv_issue_date ON finance.finance_invoice(issue_date);
CREATE INDEX IF NOT EXISTS idx_inv_due_date ON finance.finance_invoice(due_date);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_inv_tenant_status ON finance.finance_invoice(tenant_id, status)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_inv_tenant_partner ON finance.finance_invoice(tenant_id, trading_partner_id)
    WHERE is_active = TRUE;

-- Overdue invoices optimization
CREATE INDEX IF NOT EXISTS idx_inv_overdue ON finance.finance_invoice(tenant_id, due_date ASC)
    WHERE is_active = TRUE AND status IN ('SENT', 'PARTIALLY_PAID');

-- Unpaid invoices optimization
CREATE INDEX IF NOT EXISTS idx_inv_unpaid ON finance.finance_invoice(tenant_id, due_date ASC)
    WHERE is_active = TRUE AND status IN ('SENT', 'PARTIALLY_PAID', 'OVERDUE');

-- AR/AP queries
CREATE INDEX IF NOT EXISTS idx_inv_ar ON finance.finance_invoice(tenant_id, issue_date DESC)
    WHERE is_active = TRUE AND invoice_type = 'SALES';

CREATE INDEX IF NOT EXISTS idx_inv_ap ON finance.finance_invoice(tenant_id, issue_date DESC)
    WHERE is_active = TRUE AND invoice_type = 'PURCHASE';

-- JSONB index
CREATE INDEX IF NOT EXISTS idx_inv_metadata ON finance.finance_invoice USING GIN (metadata)
    WHERE metadata IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 4: Comments
-- ═══════════════════════════════════════════════════════════════════════════
COMMENT ON TABLE finance.finance_invoice IS 
'Invoices (AR and AP) with TradingPartner integration. Uses trading_partner_id for customer/vendor reference.';

COMMENT ON COLUMN finance.finance_invoice.trading_partner_id IS 
'FK to TradingPartner. For SALES invoices: customer. For PURCHASE invoices: vendor/supplier.';

COMMENT ON COLUMN finance.finance_invoice.invoice_type IS 
'SALES (AR), PURCHASE (AP), CREDIT_NOTE, DEBIT_NOTE, PROFORMA';

COMMENT ON COLUMN finance.finance_invoice.status IS 
'DRAFT, ISSUED, SENT, PARTIALLY_PAID, PAID, CANCELLED, VOIDED, OVERDUE, DISPUTED';

-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK
-- ═══════════════════════════════════════════════════════════════════════════
-- DROP TABLE IF EXISTS finance.finance_invoice CASCADE;
-- DROP SCHEMA IF EXISTS finance CASCADE;
