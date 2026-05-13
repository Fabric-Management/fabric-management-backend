-- GoodsReceipt + GoodsReceiptItem tables: V20260317140000

CREATE TABLE IF NOT EXISTS production.goods_receipt (
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

    -- Business key
    receipt_number VARCHAR(50) NOT NULL UNIQUE,

    -- Polymorphic source
    source_type VARCHAR(30) NOT NULL
        CONSTRAINT ck_gr_source_type CHECK (source_type IN ('BATCH', 'PURCHASE_ORDER', 'SUBCONTRACT_ORDER')),
    source_id UUID NOT NULL,

    -- Physical receipt info
    received_by_id UUID NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    package_count INT NOT NULL,
    gross_weight DECIMAL(15,3),
    net_weight DECIMAL(15,3),
    vehicle_info VARCHAR(255),
    damage_notes TEXT,

    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_gr_status CHECK (status IN ('DRAFT', 'CONFIRMED'))
);

CREATE TABLE IF NOT EXISTS production.goods_receipt_item (
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

    goods_receipt_id UUID NOT NULL,
    sequence_no INT NOT NULL,
    barcode VARCHAR(100) NOT NULL,
    serial_number VARCHAR(100),
    net_weight DECIMAL(15,3) NOT NULL,
    gross_weight DECIMAL(15,3),
    notes TEXT
);

-- ── goods_receipt indexes ─────────────────────────────────────────────────────

-- Tenant-scoped listing
CREATE INDEX IF NOT EXISTS idx_gr_tenant_id
    ON production.goods_receipt(tenant_id);

-- Status dashboard (warehouse team)
CREATE INDEX IF NOT EXISTS idx_gr_status_tenant
    ON production.goods_receipt(tenant_id, status)
    WHERE is_active = true;

-- Polymorphic source lookup (all receipts for a given PO/SC/Batch)
CREATE INDEX IF NOT EXISTS idx_gr_source
    ON production.goods_receipt(source_type, source_id)
    WHERE is_active = true;

-- Received-by user (personnel audit)
CREATE INDEX IF NOT EXISTS idx_gr_received_by_id
    ON production.goods_receipt(received_by_id);

-- ── goods_receipt_item indexes ────────────────────────────────────────────────

-- Primary: all items for a receipt (ordered by sequence_no in service)
CREATE INDEX IF NOT EXISTS idx_gri_receipt_id
    ON production.goods_receipt_item(goods_receipt_id);

-- Barcode scan lookup (must be fast — warehouse floor usage)
CREATE UNIQUE INDEX IF NOT EXISTS idx_gri_barcode_active
    ON production.goods_receipt_item(barcode)
    WHERE is_active = true;

-- Sequence uniqueness per receipt
CREATE UNIQUE INDEX IF NOT EXISTS idx_gri_receipt_seq
    ON production.goods_receipt_item(goods_receipt_id, sequence_no)
    WHERE is_active = true AND deleted_at IS NULL;
