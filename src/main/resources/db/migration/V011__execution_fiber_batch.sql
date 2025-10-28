-- =====================================================
-- EXECUTION: Fiber Batch Tracking
-- =====================================================
-- This schema tracks physical fiber lots/batches in production
-- Each batch represents actual inventory that can be used in production

CREATE TABLE IF NOT EXISTS production.production_execution_fiber_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    fiber_id UUID NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    supplier_batch_code VARCHAR(100),
    
    quantity DECIMAL(15,3) NOT NULL,
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
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_exec_fiber_batch_tenant_id ON production.production_execution_fiber_batch(tenant_id);
CREATE INDEX idx_exec_fiber_batch_fiber_id ON production.production_execution_fiber_batch(fiber_id);
CREATE INDEX idx_exec_fiber_batch_code ON production.production_execution_fiber_batch(batch_code);
CREATE INDEX idx_exec_fiber_batch_status ON production.production_execution_fiber_batch(status);
CREATE INDEX idx_exec_fiber_batch_warehouse_location ON production.production_execution_fiber_batch(warehouse_location);

COMMENT ON TABLE production.production_execution_fiber_batch IS 'Tracks physical fiber lots/batches for production execution. Links to masterdata fiber via fiber_id.';
COMMENT ON COLUMN production.production_execution_fiber_batch.fiber_id IS 'References production.prod_fiber.id (masterdata)';
COMMENT ON COLUMN production.production_execution_fiber_batch.batch_code IS 'Unique batch identifier (e.g., "BATCH-2025-001")';
COMMENT ON COLUMN production.production_execution_fiber_batch.supplier_batch_code IS 'Supplier-provided batch code';
COMMENT ON COLUMN production.production_execution_fiber_batch.quantity IS 'Available quantity in this batch';
COMMENT ON COLUMN production.production_execution_fiber_batch.status IS 'Batch lifecycle: NEW, RESERVED, IN_USE, DEPLETED';

