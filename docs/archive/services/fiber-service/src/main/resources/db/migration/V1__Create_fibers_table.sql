-- =====================================================
-- Fiber Service - Initial Schema
-- Version: 1.0
-- Description: Creates fibers and fiber_components tables
-- =====================================================

-- Create fibers table
CREATE TABLE IF NOT EXISTS fibers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    composition_type VARCHAR(10) NOT NULL,
    origin_type VARCHAR(20) NOT NULL,
    sustainability_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    reusable BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- FiberProperty embedded fields
    staple_length DECIMAL(10,2),
    fineness DECIMAL(10,2),
    tenacity DECIMAL(10,2),
    moisture_regain DECIMAL(10,2),
    color VARCHAR(50),
    
    -- Audit fields (from BaseEntity)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Constraints
    CONSTRAINT uk_fiber_code UNIQUE (code),
    CONSTRAINT chk_category CHECK (category IN ('NATURAL', 'SYNTHETIC', 'ARTIFICIAL', 'MINERAL', 'BLEND')),
    CONSTRAINT chk_composition CHECK (composition_type IN ('PURE', 'BLEND')),
    CONSTRAINT chk_origin CHECK (origin_type IN ('PLANT', 'ANIMAL', 'MINERAL', 'PETROLEUM', 'CELLULOSIC', 'UNKNOWN')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

-- Create fiber_components table (for blend fibers)
CREATE TABLE IF NOT EXISTS fiber_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fiber_id UUID NOT NULL,
    fiber_code VARCHAR(20) NOT NULL,
    percentage DECIMAL(5,2) NOT NULL,
    sustainability_type VARCHAR(30),
    
    CONSTRAINT fk_fiber_component FOREIGN KEY (fiber_id) REFERENCES fibers(id) ON DELETE CASCADE,
    CONSTRAINT chk_percentage CHECK (percentage > 0 AND percentage <= 100)
);

-- Create indexes
CREATE INDEX idx_fiber_tenant ON fibers(tenant_id) WHERE deleted = FALSE;
CREATE INDEX idx_fiber_category ON fibers(category) WHERE deleted = FALSE;
CREATE INDEX idx_fiber_status ON fibers(status) WHERE deleted = FALSE;
CREATE INDEX idx_fiber_composition ON fibers(composition_type) WHERE deleted = FALSE;
CREATE INDEX idx_fiber_default ON fibers(is_default) WHERE deleted = FALSE AND is_default = TRUE;
CREATE INDEX idx_fiber_code_search ON fibers(code) WHERE deleted = FALSE;
CREATE INDEX idx_fiber_name_search ON fibers(name) WHERE deleted = FALSE;
CREATE INDEX idx_fiber_component_fiber ON fiber_components(fiber_id);

-- Comments
COMMENT ON TABLE fibers IS 'Core fiber definitions - both pure and blend fibers';
COMMENT ON TABLE fiber_components IS 'Blend fiber composition - stores component percentages';
COMMENT ON COLUMN fibers.code IS 'Unique fiber code (e.g., CO, WO, PES, BLD-001)';
COMMENT ON COLUMN fibers.is_default IS 'System-defined 100% pure fibers (immutable)';
COMMENT ON COLUMN fibers.reusable IS 'Can be used as component in blend fibers';
COMMENT ON COLUMN fibers.deleted IS 'Soft delete flag - used by @SQLRestriction';

