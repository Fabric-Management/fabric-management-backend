-- Create Recipe tables: V20250317130000__create_recipe_tables.sql

CREATE TABLE IF NOT EXISTS production.prod_recipe (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    
    name VARCHAR(500) NOT NULL,
    iso_code VARCHAR(255) NOT NULL,
    components JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    recipe_version INT NOT NULL,
    parent_recipe_id UUID
);

CREATE TABLE IF NOT EXISTS production.prod_recipe_component (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    
    recipe_id UUID NOT NULL,
    fiber_id UUID NOT NULL,
    fiber_name VARCHAR(255) NOT NULL,
    fiber_iso_code VARCHAR(10) NOT NULL,
    percentage DECIMAL(5,2) NOT NULL,
    certification VARCHAR(50),
    origin VARCHAR(10),
    display_order INT NOT NULL
);

-- Unique constraint for active records
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_recipe_component 
    ON production.prod_recipe_component(recipe_id, fiber_id) 
    WHERE is_active = true AND deleted_at IS NULL;

-- Fast search indexes
CREATE INDEX IF NOT EXISTS idx_recipe_component_recipe_id ON production.prod_recipe_component(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_component_certification ON production.prod_recipe_component(certification);
CREATE INDEX IF NOT EXISTS idx_recipe_component_origin ON production.prod_recipe_component(origin);
