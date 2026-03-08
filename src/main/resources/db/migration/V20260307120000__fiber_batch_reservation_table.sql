-- =====================================================
-- V20260307120000: Named Reservation System (FiberBatchReservation)
-- =====================================================
-- Fixes the "blind reservation" bug where consume() indiscriminately
-- ate from the global reservedQuantity pool. Each reservation is now
-- tied to a specific reference (work order, sample request, etc.)
-- so that ad-hoc consumption cannot steal reserved material.
--
-- Also updates the inventory_transaction type constraint to support
-- RESERVATION and RESERVATION_RELEASE audit entries.
-- =====================================================

-- ── Part A: Reservation Table ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS production.production_execution_fiber_batch_reservation (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    uid                 VARCHAR(100)    NOT NULL,

    batch_id            UUID            NOT NULL,
    reference_id        UUID,
    reference_type      VARCHAR(50)     NOT NULL,

    reserved_quantity   DECIMAL(15,3)   NOT NULL,
    consumed_quantity   DECIMAL(15,3)   NOT NULL DEFAULT 0,
    unit                VARCHAR(20)     NOT NULL,

    status              VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    reserved_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks             TEXT,

    is_active           BOOLEAN         NOT NULL DEFAULT true,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_fiber_batch_reservation
        PRIMARY KEY (id),
    CONSTRAINT fk_reservation_batch
        FOREIGN KEY (batch_id)
        REFERENCES production.production_execution_fiber_batch(id),
    CONSTRAINT uq_reservation_tenant_uid
        UNIQUE (tenant_id, uid),
    CONSTRAINT ck_reservation_qty_positive
        CHECK (reserved_quantity > 0),
    CONSTRAINT ck_reservation_consumed_nonneg
        CHECK (consumed_quantity >= 0),
    CONSTRAINT ck_reservation_consumed_within
        CHECK (consumed_quantity <= reserved_quantity),
    CONSTRAINT ck_reservation_status_valid
        CHECK (status IN ('ACTIVE', 'PARTIALLY_CONSUMED', 'FULFILLED', 'CANCELLED'))
);

-- Partial unique index: only one active reservation per (batch, reference) pair
CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_active_ref
    ON production.production_execution_fiber_batch_reservation(batch_id, reference_id, reference_type)
    WHERE status IN ('ACTIVE', 'PARTIALLY_CONSUMED') AND is_active = true;

CREATE INDEX IF NOT EXISTS idx_reservation_tenant
    ON production.production_execution_fiber_batch_reservation(tenant_id);
CREATE INDEX IF NOT EXISTS idx_reservation_batch
    ON production.production_execution_fiber_batch_reservation(batch_id);
CREATE INDEX IF NOT EXISTS idx_reservation_status
    ON production.production_execution_fiber_batch_reservation(status);
CREATE INDEX IF NOT EXISTS idx_reservation_reference
    ON production.production_execution_fiber_batch_reservation(reference_id)
    WHERE reference_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reservation_tenant_batch
    ON production.production_execution_fiber_batch_reservation(tenant_id, batch_id);

COMMENT ON TABLE production.production_execution_fiber_batch_reservation IS
    'Named reservations tying a specific quantity of a FiberBatch to a reference entity (work order, sample request, etc.). Prevents blind reservation erosion.';
COMMENT ON COLUMN production.production_execution_fiber_batch_reservation.reference_id IS
    'FK to the entity that owns this reservation (e.g. work order UUID). Nullable for MANUAL reservations.';
COMMENT ON COLUMN production.production_execution_fiber_batch_reservation.reference_type IS
    'Discriminator: WORK_ORDER, SAMPLE_REQUEST, MANUAL, LEGACY';
COMMENT ON COLUMN production.production_execution_fiber_batch_reservation.consumed_quantity IS
    'How much of this reservation has been consumed so far. When consumed_quantity = reserved_quantity, status = FULFILLED.';

-- ── Part B: Expand inventory transaction type constraint ─────────────────────

ALTER TABLE production.production_execution_inventory_transaction
    DROP CONSTRAINT IF EXISTS ck_inv_txn_type_valid;

ALTER TABLE production.production_execution_inventory_transaction
    ADD CONSTRAINT ck_inv_txn_type_valid
        CHECK (transaction_type IN (
            'RECEIPT', 'CONSUMPTION', 'WASTE',
            'ADJUSTMENT', 'TRANSFER', 'RETURN', 'SAMPLE',
            'RESERVATION', 'RESERVATION_RELEASE'
        ));

-- ── Part C: Migrate existing reserved quantities to LEGACY reservations ──────

INSERT INTO production.production_execution_fiber_batch_reservation (
    id, tenant_id, uid,
    batch_id, reference_id, reference_type,
    reserved_quantity, consumed_quantity, unit,
    status, reserved_at, remarks,
    is_active, created_at, updated_at, version
)
SELECT
    gen_random_uuid(),
    b.tenant_id,
    'EXEC-FBRES-LEGACY-' || b.batch_code,
    b.id,
    NULL,
    'LEGACY',
    b.reserved_quantity,
    0,
    b.unit,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    'Auto-migrated from anonymous reserved_quantity during Named Reservation migration.',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM production.production_execution_fiber_batch b
WHERE b.reserved_quantity > 0
  AND b.is_active = true;
