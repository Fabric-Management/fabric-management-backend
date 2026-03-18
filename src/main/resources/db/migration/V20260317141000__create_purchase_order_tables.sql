-- PurchaseOrder + PurchaseOrderLine tables: V20260317141000

CREATE TABLE IF NOT EXISTS procurement.purchase_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    po_number VARCHAR(50) NOT NULL UNIQUE,
    work_order_id UUID NOT NULL,
    trading_partner_id UUID NOT NULL,
    supplier_quote_id UUID,
    status VARCHAR(25) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_po_status CHECK (status IN (
            'DRAFT', 'SENT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'
        )),
    currency VARCHAR(3) NOT NULL,
    payment_terms VARCHAR(20),
    expected_delivery DATE,
    total_amount DECIMAL(18,3) NOT NULL,
    revision_number INT NOT NULL DEFAULT 1,
    change_reason TEXT,
    notes TEXT,
    attachments JSONB
);

CREATE TABLE IF NOT EXISTS procurement.purchase_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    purchase_order_id UUID NOT NULL,
    rfq_line_id UUID,
    material_id UUID,
    product_desc TEXT,
    qty DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_price DECIMAL(18,3) NOT NULL
);

-- ── purchase_order indexes ────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_po_tenant_id
    ON procurement.purchase_order(tenant_id);

CREATE INDEX IF NOT EXISTS idx_po_status_tenant
    ON procurement.purchase_order(tenant_id, status)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_po_work_order_id
    ON procurement.purchase_order(work_order_id)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_po_trading_partner_id
    ON procurement.purchase_order(trading_partner_id)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_po_expected_delivery
    ON procurement.purchase_order(expected_delivery)
    WHERE expected_delivery IS NOT NULL AND is_active = true;

-- ── purchase_order_line indexes ───────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_pol_purchase_order_id
    ON procurement.purchase_order_line(purchase_order_id);

CREATE INDEX IF NOT EXISTS idx_pol_material_id
    ON procurement.purchase_order_line(material_id)
    WHERE material_id IS NOT NULL;
