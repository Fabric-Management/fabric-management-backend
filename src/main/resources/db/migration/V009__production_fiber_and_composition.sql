-- =====================================================
-- V9: PRODUCTION - FIBER & COMPOSITION
-- =====================================================
-- Purpose: Fiber entity + Many-to-Many for blended compositions
-- Date: 2025-10-27
-- =====================================================

-- =====================================================
-- Material Table (Base Catalog)
-- =====================================================

CREATE TABLE production.prod_material (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    material_type VARCHAR(20) NOT NULL CHECK (material_type IN ('FIBER', 'YARN', 'FABRIC', 'CHEMICAL', 'CONSUMABLE')),
    unit VARCHAR(20) NOT NULL,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_material_tenant_type ON production.prod_material(tenant_id, material_type);
CREATE INDEX idx_material_active ON production.prod_material(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_material_tenant_active ON production.prod_material(tenant_id, is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_material IS 'Material master data - READ-ONLY catalog';
COMMENT ON COLUMN production.prod_material.material_type IS 'FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE';

-- =====================================================
-- Fiber Table (Concrete Fiber Instances)
-- =====================================================

CREATE TABLE production.prod_fiber (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- Relationships
    material_id UUID NOT NULL UNIQUE REFERENCES production.prod_material(id) ON DELETE CASCADE,
    fiber_category_id UUID REFERENCES production.prod_fiber_category(id),
    fiber_iso_code_id UUID REFERENCES production.prod_fiber_iso_code(id),
    
    -- Identity
    fiber_name VARCHAR(255) NOT NULL,
    fiber_grade VARCHAR(50),
    
    -- Technical specifications (pure fibers only)
    fineness DOUBLE PRECISION,
    length_mm DOUBLE PRECISION,
    strength_cn_dtex DOUBLE PRECISION,
    elongation_percent DOUBLE PRECISION,
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' 
        CHECK (status IN ('NEW', 'IN_USE', 'EXHAUSTED', 'OBSOLETE')),
    remarks TEXT,
    
    -- Base entity fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_tenant ON production.prod_fiber(tenant_id);
CREATE INDEX idx_fiber_material ON production.prod_fiber(material_id);
CREATE INDEX idx_fiber_category ON production.prod_fiber(fiber_category_id);
CREATE INDEX idx_fiber_iso ON production.prod_fiber(fiber_iso_code_id);
CREATE INDEX idx_fiber_status ON production.prod_fiber(status);
CREATE INDEX idx_fiber_tenant_active ON production.prod_fiber(tenant_id, is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber IS 'Fiber instances - Can be pure (100%) or blended';
COMMENT ON COLUMN production.prod_fiber.fiber_iso_code_id IS 'FK to fiber ISO code (CO, PES, PA, etc.)';
COMMENT ON COLUMN production.prod_fiber.fineness IS 'Micronaire or dtex (only for pure fibers)';
COMMENT ON COLUMN production.prod_fiber.status IS 'NEW, IN_USE, EXHAUSTED, OBSOLETE';

-- =====================================================
-- Fiber Composition Table (Many-to-Many)
-- =====================================================
-- For blended fibers: links base fibers with their percentage in blend
-- Total % must sum to 100 (enforced by application logic)

CREATE TABLE production.prod_fiber_composition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    
    -- Relationships
    blended_fiber_id UUID NOT NULL REFERENCES production.prod_fiber(id) ON DELETE CASCADE,
    base_fiber_id UUID NOT NULL REFERENCES production.prod_fiber(id) ON DELETE CASCADE,
    
    -- Percentage in blend (must sum to 100% for each blended_fiber_id)
    percentage NUMERIC(5,2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    
    -- Base entity fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_composition_blend_base UNIQUE (blended_fiber_id, base_fiber_id)
);

CREATE INDEX idx_fiber_composition_blend ON production.prod_fiber_composition(blended_fiber_id);
CREATE INDEX idx_fiber_composition_base ON production.prod_fiber_composition(base_fiber_id);
CREATE INDEX idx_fiber_composition_active ON production.prod_fiber_composition(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_composition IS 'Fiber blend composition - Many-to-Many';
COMMENT ON COLUMN production.prod_fiber_composition.percentage IS 'Percentage of base_fiber in blended_fiber (must sum to 100)';

-- =====================================================
-- Fiber ↔ Attribute Link Table (Many-to-Many)
-- =====================================================

CREATE TABLE production.prod_fiber_attribute_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    fiber_id UUID NOT NULL REFERENCES production.prod_fiber(id) ON DELETE CASCADE,
    attribute_id UUID NOT NULL REFERENCES production.prod_fiber_attribute(id) ON DELETE CASCADE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_fiber_attribute UNIQUE (fiber_id, attribute_id)
);

CREATE INDEX idx_fiber_attr_link_fiber ON production.prod_fiber_attribute_link(fiber_id);
CREATE INDEX idx_fiber_attr_link_attr ON production.prod_fiber_attribute_link(attribute_id);
CREATE INDEX idx_fiber_attr_link_active ON production.prod_fiber_attribute_link(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_attribute_link IS 'Fiber attributes - Many-to-Many (ORGANIC, RECYCLED, etc.)';

-- =====================================================
-- Fiber ↔ Certification Link Table (Many-to-Many)
-- =====================================================

CREATE TABLE production.prod_fiber_certification_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    fiber_id UUID NOT NULL REFERENCES production.prod_fiber(id) ON DELETE CASCADE,
    certification_id UUID NOT NULL REFERENCES production.prod_fiber_certification(id) ON DELETE CASCADE,
    
    cert_number VARCHAR(100),
    valid_from DATE,
    valid_until DATE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_fiber_certification UNIQUE (fiber_id, certification_id)
);

CREATE INDEX idx_fiber_cert_link_fiber ON production.prod_fiber_certification_link(fiber_id);
CREATE INDEX idx_fiber_cert_link_cert ON production.prod_fiber_certification_link(certification_id);
CREATE INDEX idx_fiber_cert_link_active ON production.prod_fiber_certification_link(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_certification_link IS 'Fiber certifications - Many-to-Many (GOTS, OEKO_TEX, etc.)';

-- =====================================================
-- COMPLETED: V9 - FIBER & COMPOSITION
-- =====================================================

