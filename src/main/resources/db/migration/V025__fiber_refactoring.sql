-- =====================================================
-- V25: FIBER MODULE REFACTORING
-- =====================================================
-- Purpose: Complete fiber module refactoring
-- Date: 2025-01-27
-- Changes:
--   1. Composition: Junction table → JSONB column
--   2. Status: NEW/IN_USE/EXHAUSTED → ACTIVE/OBSOLETE
--   3. Measurements: Removed from Fiber (belong to FiberTestResult)
--   4. New: FiberTestResult table for laboratory measurements
-- =====================================================

-- =====================================================
-- PART 1: COMPOSITION JSONB MIGRATION
-- =====================================================

-- Step 1.1: Add composition JSONB column to Fiber table
ALTER TABLE production.prod_fiber
ADD COLUMN IF NOT EXISTS composition JSONB;

-- Step 1.2: Migrate existing composition data from junction table
UPDATE production.prod_fiber f
SET composition = (
    SELECT jsonb_object_agg(
        base_fiber_id::text, 
        percentage::text
    )
    FROM production.prod_fiber_composition fc
    WHERE fc.blended_fiber_id = f.id
    AND fc.is_active = true
)
WHERE EXISTS (
    SELECT 1 FROM production.prod_fiber_composition fc
    WHERE fc.blended_fiber_id = f.id
    AND fc.is_active = true
);

-- Step 1.3: Create GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_fiber_composition_gin 
ON production.prod_fiber 
USING GIN (composition);

-- Step 1.4: Drop junction table (composition now in Fiber.composition JSONB)
DROP TABLE IF EXISTS production.prod_fiber_composition CASCADE;

-- =====================================================
-- PART 2: STATUS CONSTRAINT UPDATE
-- =====================================================

-- Step 2.1: Update status check constraint (ACTIVE/OBSOLETE)
ALTER TABLE production.prod_fiber
DROP CONSTRAINT IF EXISTS prod_fiber_status_check;

ALTER TABLE production.prod_fiber
ADD CONSTRAINT prod_fiber_status_check 
CHECK (status IN ('ACTIVE', 'OBSOLETE'));

-- Step 2.2: Update default status to ACTIVE (if not already)
ALTER TABLE production.prod_fiber
ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- =====================================================
-- PART 3: REMOVE LABORATORY MEASUREMENTS FROM FIBER
-- =====================================================
-- Reason: Fiber = catalog definition, measurements belong to FiberBatch/TestResult

-- Step 3.1: Remove measurement columns from Fiber table
ALTER TABLE production.prod_fiber
DROP COLUMN IF EXISTS fineness,
DROP COLUMN IF EXISTS length_mm,
DROP COLUMN IF EXISTS strength_cn_dtex,
DROP COLUMN IF EXISTS elongation_percent;

-- =====================================================
-- PART 4: CREATE FIBER TEST RESULT TABLE
-- =====================================================
-- Purpose: Store laboratory test results for fiber batches

CREATE TABLE IF NOT EXISTS production.production_quality_fiber_test_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    fiber_batch_id UUID NOT NULL REFERENCES production.production_execution_fiber_batch(id) ON DELETE CASCADE,
    test_date TIMESTAMP WITH TIME ZONE NOT NULL,
    test_type VARCHAR(50) NOT NULL DEFAULT 'LABORATORY',
    
    -- Laboratory measurement values
    fineness DOUBLE PRECISION,
    length_mm DOUBLE PRECISION,
    strength_cn_dtex DOUBLE PRECISION,
    elongation_percent DOUBLE PRECISION,
    
    test_lab VARCHAR(255),
    test_standard VARCHAR(100), -- ISO 1833, ASTM D7641, etc.
    remarks TEXT,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_test_batch ON production.production_quality_fiber_test_result(fiber_batch_id);
CREATE INDEX idx_fiber_test_date ON production.production_quality_fiber_test_result(test_date);
CREATE INDEX idx_fiber_test_tenant ON production.production_quality_fiber_test_result(tenant_id);
CREATE INDEX idx_fiber_test_tenant_active ON production.production_quality_fiber_test_result(tenant_id, is_active) WHERE is_active = TRUE;

-- =====================================================
-- PART 5: UPDATE COMMENTS
-- =====================================================

COMMENT ON COLUMN production.prod_fiber.composition IS 
'Fiber composition map as JSONB: {baseFiberId: percentage}. 
Empty or null for pure fibers (100% single fiber). 
Example: {"uuid1": "60.00", "uuid2": "40.00"} for 60%+40% blend.';

COMMENT ON COLUMN production.prod_fiber.fiber_name IS 'Fiber name (catalog definition)';
COMMENT ON COLUMN production.prod_fiber.fiber_grade IS 'Fiber grade/class (A, B, C, etc.)';
COMMENT ON COLUMN production.prod_fiber.status IS 'ACTIVE or OBSOLETE (catalog lifecycle)';
COMMENT ON TABLE production.prod_fiber IS 
'Fiber catalog definitions - Pure (100%) or blended compositions. 
Laboratory measurements (fineness, length, strength, elongation) belong to FiberBatch or FiberTestResult entities.';

COMMENT ON TABLE production.production_quality_fiber_test_result IS 
'Laboratory test results for fiber batches. 
Each result records physical measurements (fineness, length, strength, elongation) from a specific test date.';

COMMENT ON COLUMN production.production_quality_fiber_test_result.test_type IS 
'LABORATORY, PRODUCTION, or INCOMING quality check';

COMMENT ON COLUMN production.production_quality_fiber_test_result.test_standard IS 
'Test standard used (e.g., ISO 1833, ASTM D7641)';

