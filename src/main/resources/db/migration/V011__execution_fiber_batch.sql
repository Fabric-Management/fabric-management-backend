-- =====================================================
-- EXECUTION: Fiber Batch Tracking (Production-Ready)
-- =====================================================
-- This schema tracks physical fiber lots/batches in production
-- Prevents over-issue and race conditions with reservation logic

CREATE TABLE IF NOT EXISTS production.production_execution_fiber_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    
    fiber_id UUID NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    supplier_batch_code VARCHAR(100),
    
    -- Critical: Prevents over-issue and race conditions
    quantity DECIMAL(15,3) NOT NULL,
    reserved_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    consumed_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    
    unit VARCHAR(20) NOT NULL,
    
    production_date TIMESTAMP WITH TIME ZONE,
    expiry_date TIMESTAMP WITH TIME ZONE,
    
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    warehouse_location VARCHAR(100),
    remarks TEXT,
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT production_execution_fiber_batch_pkey PRIMARY KEY (id),
    CONSTRAINT fk_batch_fiber FOREIGN KEY (fiber_id) REFERENCES production.prod_fiber(id),
    CONSTRAINT uq_batch_tenant_code UNIQUE (tenant_id, batch_code),
    CONSTRAINT uq_batch_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT ck_qty_nonneg CHECK (
      quantity >= 0 AND reserved_quantity >= 0 AND consumed_quantity >= 0
    ),
    CONSTRAINT ck_qty_bounds CHECK (
      reserved_quantity + consumed_quantity <= quantity
    )
);

-- Indexes for common query patterns
CREATE INDEX idx_exec_fiber_batch_tenant_id ON production.production_execution_fiber_batch(tenant_id);
CREATE INDEX idx_exec_fiber_batch_fiber_id ON production.production_execution_fiber_batch(fiber_id);
CREATE INDEX idx_exec_fiber_batch_code ON production.production_execution_fiber_batch(batch_code);
CREATE INDEX idx_exec_fiber_batch_status ON production.production_execution_fiber_batch(status);
CREATE INDEX idx_exec_fiber_batch_warehouse_location ON production.production_execution_fiber_batch(warehouse_location);

-- Composite indexes for complex queries
CREATE INDEX idx_batch_tenant_fiber_status ON production.production_execution_fiber_batch (tenant_id, fiber_id, status);
CREATE INDEX idx_batch_tenant_loc_status ON production.production_execution_fiber_batch (tenant_id, warehouse_location, status);

COMMENT ON TABLE production.production_execution_fiber_batch IS 'Tracks physical fiber lots/batches for production execution. Prevents over-issue with reserved_quantity and consumed_quantity tracking.';
COMMENT ON COLUMN production.production_execution_fiber_batch.fiber_id IS 'References production.prod_fiber.id (masterdata)';
COMMENT ON COLUMN production.production_execution_fiber_batch.batch_code IS 'Tenant-scoped unique batch identifier (e.g., "BATCH-2025-001")';
COMMENT ON COLUMN production.production_execution_fiber_batch.supplier_batch_code IS 'Supplier-provided batch code';
COMMENT ON COLUMN production.production_execution_fiber_batch.quantity IS 'Total quantity in this batch';
COMMENT ON COLUMN production.production_execution_fiber_batch.reserved_quantity IS 'Quantity reserved for production orders (prevents over-issue)';
COMMENT ON COLUMN production.production_execution_fiber_batch.consumed_quantity IS 'Quantity actually consumed in production';
COMMENT ON COLUMN production.production_execution_fiber_batch.status IS 'Batch lifecycle: NEW, RESERVED, IN_USE, DEPLETED';
