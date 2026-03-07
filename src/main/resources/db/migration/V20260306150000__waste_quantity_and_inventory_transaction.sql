-- =====================================================
-- V20260306150000: Waste Tracking + Inventory Transaction Ledger
-- =====================================================
-- Part A: Denormalized waste_quantity on FiberBatch for fast reads.
--   waste_quantity is a SUBSET of consumed_quantity (not additive).
--   consumed = useful_output + waste  →  net_output = consumed - waste
--   available = quantity - reserved - consumed  (unchanged)
--
-- Part B: Immutable inventory transaction ledger (SAP MM style).
--   Every stock movement (receive, consume, waste, adjust, transfer)
--   is recorded as an event for audit, reconciliation, and analytics.
-- =====================================================

-- ── Part A: waste_quantity on FiberBatch ─────────────────────────────────────

ALTER TABLE production.production_execution_fiber_batch
    ADD COLUMN IF NOT EXISTS waste_quantity DECIMAL(15,3) NOT NULL DEFAULT 0;

ALTER TABLE production.production_execution_fiber_batch
    DROP CONSTRAINT IF EXISTS ck_qty_bounds;

ALTER TABLE production.production_execution_fiber_batch
    ADD CONSTRAINT ck_qty_bounds
        CHECK (reserved_quantity + consumed_quantity <= quantity);

ALTER TABLE production.production_execution_fiber_batch
    ADD CONSTRAINT ck_waste_nonneg
        CHECK (waste_quantity >= 0);

ALTER TABLE production.production_execution_fiber_batch
    ADD CONSTRAINT ck_waste_within_consumed
        CHECK (waste_quantity <= consumed_quantity);

COMMENT ON COLUMN production.production_execution_fiber_batch.waste_quantity IS
    'Subset of consumed_quantity that was lost as production waste (fire/telef). net_output = consumed_quantity - waste_quantity';

-- ── Part B: Inventory Transaction Ledger ─────────────────────────────────────

CREATE TABLE IF NOT EXISTS production.production_execution_inventory_transaction (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    uid                 VARCHAR(100)    NOT NULL,

    batch_id            UUID            NOT NULL,
    transaction_type    VARCHAR(30)     NOT NULL,
    quantity            DECIMAL(15,3)   NOT NULL,
    unit                VARCHAR(20)     NOT NULL,

    transaction_date    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_id        UUID,
    reference_type      VARCHAR(50),
    reason              VARCHAR(255),
    remarks             TEXT,

    is_active           BOOLEAN         NOT NULL DEFAULT true,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventory_transaction
        PRIMARY KEY (id),
    CONSTRAINT fk_inv_txn_batch
        FOREIGN KEY (batch_id)
        REFERENCES production.production_execution_fiber_batch(id),
    CONSTRAINT uq_inv_txn_tenant_uid
        UNIQUE (tenant_id, uid),
    CONSTRAINT ck_inv_txn_qty_positive
        CHECK (quantity > 0),
    CONSTRAINT ck_inv_txn_type_valid
        CHECK (transaction_type IN (
            'RECEIPT', 'CONSUMPTION', 'WASTE',
            'ADJUSTMENT', 'TRANSFER', 'RETURN', 'SAMPLE'
        ))
);

CREATE INDEX IF NOT EXISTS idx_inv_txn_tenant
    ON production.production_execution_inventory_transaction(tenant_id);
CREATE INDEX IF NOT EXISTS idx_inv_txn_batch
    ON production.production_execution_inventory_transaction(batch_id);
CREATE INDEX IF NOT EXISTS idx_inv_txn_type
    ON production.production_execution_inventory_transaction(transaction_type);
CREATE INDEX IF NOT EXISTS idx_inv_txn_date
    ON production.production_execution_inventory_transaction(transaction_date);
CREATE INDEX IF NOT EXISTS idx_inv_txn_tenant_batch
    ON production.production_execution_inventory_transaction(tenant_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_inv_txn_reference
    ON production.production_execution_inventory_transaction(reference_id)
    WHERE reference_id IS NOT NULL;

COMMENT ON TABLE production.production_execution_inventory_transaction IS
    'Immutable ledger of all stock movements. Each row is an event: receipt, consumption, waste, adjustment, etc. Enables full audit trail, waste analysis, and inventory reconciliation.';
COMMENT ON COLUMN production.production_execution_inventory_transaction.transaction_type IS
    'RECEIPT=incoming goods, CONSUMPTION=production use, WASTE=fire/telef, ADJUSTMENT=count correction, TRANSFER=warehouse move, RETURN=supplier return, SAMPLE=lab sample';
COMMENT ON COLUMN production.production_execution_inventory_transaction.quantity IS
    'Always positive. Direction is implied by transaction_type (RECEIPT/RETURN=in, CONSUMPTION/WASTE/SAMPLE=out, ADJUSTMENT=+/- determined by reason)';
COMMENT ON COLUMN production.production_execution_inventory_transaction.reference_id IS
    'Optional FK to related entity (lineage record, production order, etc.)';
COMMENT ON COLUMN production.production_execution_inventory_transaction.reference_type IS
    'Discriminator for reference_id: LINEAGE, PRODUCTION_ORDER, QUALITY_TEST, etc.';
