-- ═══════════════════════════════════════════════════════════════
-- 0. Schema
-- ═══════════════════════════════════════════════════════════════
CREATE SCHEMA IF NOT EXISTS common_infrastructure;

-- ═══════════════════════════════════════════════════════════════
-- 1. Duplicate-check guard (H3)
--    Global UNIQUE → composite UNIQUE geçişi matematiksel olarak
--    güvenli (globally unique ⊂ tenant-unique), ancak veri
--    bütünlüğünü migration zamanında kanıtla.
-- ═══════════════════════════════════════════════════════════════
DO $$
DECLARE
    dup_count BIGINT;
BEGIN
    -- WorkOrder
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT tenant_id, work_order_number
        FROM production.prod_work_order
        WHERE is_active = true
        GROUP BY tenant_id, work_order_number
        HAVING COUNT(*) > 1
    ) x;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Duplicate active work_order_number found: % rows. Fix data before migration.', dup_count;
    END IF;

    -- PurchaseOrder
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT tenant_id, po_number
        FROM procurement.purchase_order
        WHERE is_active = true
        GROUP BY tenant_id, po_number
        HAVING COUNT(*) > 1
    ) x;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Duplicate active po_number found: % rows. Fix data before migration.', dup_count;
    END IF;

    -- SubcontractOrder
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT tenant_id, sc_number
        FROM procurement.subcontract_order
        WHERE is_active = true
        GROUP BY tenant_id, sc_number
        HAVING COUNT(*) > 1
    ) x;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Duplicate active sc_number found: % rows. Fix data before migration.', dup_count;
    END IF;

    -- SalesOrder (mevcut plain UNIQUE → partial'a geçecek)
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT tenant_id, order_number
        FROM sales_ord.sales_order
        WHERE is_active = true
        GROUP BY tenant_id, order_number
        HAVING COUNT(*) > 1
    ) x;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Duplicate active order_number found: % rows. Fix data before migration.', dup_count;
    END IF;
END $$;

-- ═══════════════════════════════════════════════════════════════
-- 2. document_sequence table (atomic upsert counter)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS common_infrastructure.document_sequence (
    tenant_id      UUID         NOT NULL,
    document_type  VARCHAR(30)  NOT NULL,
    prefix         VARCHAR(30)  NOT NULL,
    next_val       BIGINT       NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, document_type, prefix),
    -- FK karar (M4): Tenant silindiğinde document sequence'lerin de temizlenmesi
    -- referential integrity ve veri temizliği açısından en sağlıklısıdır.
    CONSTRAINT fk_document_sequence_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id)
        ON DELETE CASCADE
);

COMMENT ON TABLE common_infrastructure.document_sequence IS
    'Atomic document number generator via INSERT...ON CONFLICT...RETURNING. '
    'Key = (tenant, type, prefix). Prefix contains date so counters reset naturally per period.';

CREATE INDEX IF NOT EXISTS idx_doc_seq_tenant
    ON common_infrastructure.document_sequence(tenant_id);

-- ═══════════════════════════════════════════════════════════════
-- 3. Partial unique indexes (C1 — soft-delete safe)
-- ═══════════════════════════════════════════════════════════════

-- H4: Mevcut redundant idx_wo_tenant_wonumber index'ini drop et
DROP INDEX IF EXISTS production.idx_wo_tenant_wonumber;

-- 3a. SalesOrder: plain UNIQUE → partial unique index
ALTER TABLE sales_ord.sales_order
    DROP CONSTRAINT IF EXISTS uk_so_tenant_order_number;
CREATE UNIQUE INDEX IF NOT EXISTS uk_so_tenant_order_number_partial
    ON sales_ord.sales_order(tenant_id, order_number)
    WHERE is_active = true;

-- 3b. WorkOrder: global UNIQUE → tenant-scoped partial
ALTER TABLE production.prod_work_order
    DROP CONSTRAINT IF EXISTS prod_work_order_work_order_number_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_wo_tenant_work_order_number_partial
    ON production.prod_work_order(tenant_id, work_order_number)
    WHERE is_active = true;

-- 3c. PurchaseOrder: global UNIQUE → tenant-scoped partial
ALTER TABLE procurement.purchase_order
    DROP CONSTRAINT IF EXISTS purchase_order_po_number_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_po_tenant_po_number_partial
    ON procurement.purchase_order(tenant_id, po_number)
    WHERE is_active = true;

-- 3d. SubcontractOrder: global UNIQUE → tenant-scoped partial
ALTER TABLE procurement.subcontract_order
    DROP CONSTRAINT IF EXISTS subcontract_order_sc_number_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sc_tenant_sc_number_partial
    ON procurement.subcontract_order(tenant_id, sc_number)
    WHERE is_active = true;
