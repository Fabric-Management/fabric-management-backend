-- =====================================================
-- Material Category Table
-- =====================================================
-- Purpose: Categories for materials (FIBER, YARN, FABRIC, etc.)
-- Date: 2025-10-27
-- =====================================================

CREATE TABLE production.prod_material_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    material_type VARCHAR(20) CHECK (material_type IN ('FIBER', 'YARN', 'FABRIC', 'CHEMICAL', 'CONSUMABLE')),
    description TEXT,
    display_order INTEGER,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_category_tenant_code UNIQUE (tenant_id, category_code)
);

CREATE INDEX idx_material_category_tenant ON production.prod_material_category(tenant_id);
CREATE INDEX idx_material_category_type ON production.prod_material_category(material_type);
CREATE INDEX idx_material_category_active ON production.prod_material_category(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_material_category IS 'Material categories - tenant-specific or system-level';
