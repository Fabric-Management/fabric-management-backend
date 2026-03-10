-- =====================================================
-- V20260308100000: Enterprise Inventory Module Upgrade
-- =====================================================

-- 1. Add reason_code and idempotency_key to inventory_transaction
ALTER TABLE production.production_execution_inventory_transaction
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);

-- Allow negative quantities for adjustments
ALTER TABLE production.production_execution_inventory_transaction
    DROP CONSTRAINT IF EXISTS ck_inv_txn_qty_positive;

-- 2. Add unique constraint for idempotency
-- We only want to enforce uniqueness where idempotency_key is not null
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_txn_tenant_idempotency
    ON production.production_execution_inventory_transaction (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 3. Create read-optimized inventory balance table (CQRS Projection)
CREATE TABLE IF NOT EXISTS production.production_execution_inventory_balance (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    uid                 VARCHAR(100)    NOT NULL,
    
    batch_id            UUID            NOT NULL,
    location_id         UUID,
    
    quantity            DECIMAL(15,3)   NOT NULL DEFAULT 0,
    reserved_quantity   DECIMAL(15,3)   NOT NULL DEFAULT 0,
    consumed_quantity   DECIMAL(15,3)   NOT NULL DEFAULT 0,
    waste_quantity      DECIMAL(15,3)   NOT NULL DEFAULT 0,
    unit                VARCHAR(20)     NOT NULL,
    
    last_transaction_id UUID,
    last_transaction_date TIMESTAMP WITH TIME ZONE,
    
    is_active           BOOLEAN         NOT NULL DEFAULT true,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventory_balance
        PRIMARY KEY (id),
    CONSTRAINT uq_inv_balance_tenant_uid
        UNIQUE (tenant_id, uid),
    CONSTRAINT uq_inv_balance_batch_location
        UNIQUE (tenant_id, batch_id, location_id)
);

CREATE INDEX IF NOT EXISTS idx_inv_balance_tenant
    ON production.production_execution_inventory_balance(tenant_id);

CREATE INDEX IF NOT EXISTS idx_inv_balance_batch
    ON production.production_execution_inventory_balance(batch_id);

CREATE INDEX IF NOT EXISTS idx_inv_balance_location
    ON production.production_execution_inventory_balance(location_id);
