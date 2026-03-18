-- SubcontractOrder table: V20260317142000

CREATE TABLE IF NOT EXISTS procurement.subcontract_order (
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

    sc_number VARCHAR(50) NOT NULL UNIQUE,
    work_order_id UUID NOT NULL,
    trading_partner_id UUID NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_sc_status CHECK (status IN (
            'DRAFT', 'CONFIRMED', 'MATERIAL_SENT', 'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED'
        )),
    material_id UUID,
    material_sent_qty DECIMAL(15,3),
    unit VARCHAR(20) NOT NULL,
    actual_returned_qty DECIMAL(15,3),
    waste_qty DECIMAL(15,3),
    agreed_unit_price DECIMAL(18,4),
    currency VARCHAR(3),
    expected_return_date DATE,
    notes TEXT
);

-- ── subcontract_order indexes ─────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_sc_tenant_id
    ON procurement.subcontract_order(tenant_id);

CREATE INDEX IF NOT EXISTS idx_sc_status_tenant
    ON procurement.subcontract_order(tenant_id, status)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_sc_work_order_id
    ON procurement.subcontract_order(work_order_id)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_sc_trading_partner_id
    ON procurement.subcontract_order(trading_partner_id)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_sc_expected_return_date
    ON procurement.subcontract_order(expected_return_date)
    WHERE expected_return_date IS NOT NULL AND is_active = true;
