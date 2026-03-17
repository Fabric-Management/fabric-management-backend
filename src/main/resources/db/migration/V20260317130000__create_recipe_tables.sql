-- Create Recipe tables: V20260317130000__create_recipe_tables.sql

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

-- ── prod_recipe indexes ───────────────────────────────────────────────────────

-- Tenant-scoped listing (almost every query hits this)
CREATE INDEX IF NOT EXISTS idx_recipe_tenant_id
    ON production.prod_recipe(tenant_id);

-- Status + active filter (most common read path)
CREATE INDEX IF NOT EXISTS idx_recipe_status_active
    ON production.prod_recipe(status, is_active)
    WHERE is_active = true;

-- Name-based duplicate detection: existsByNameAndStatusAndIsActiveTrue()
-- and findActiveByName() both filter on name + active records
CREATE INDEX IF NOT EXISTS idx_recipe_name_active
    ON production.prod_recipe(name)
    WHERE is_active = true;

-- Parent recipe chain for versioning lookups
CREATE INDEX IF NOT EXISTS idx_recipe_parent_id
    ON production.prod_recipe(parent_recipe_id)
    WHERE parent_recipe_id IS NOT NULL;

-- ── prod_recipe_component indexes ────────────────────────────────────────────

-- Unique active-record guard (partial index instead of JPA @UniqueConstraint)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_recipe_component
    ON production.prod_recipe_component(recipe_id, fiber_id)
    WHERE is_active = true AND deleted_at IS NULL;

-- Bulk component fetch by recipe (primary access pattern)
CREATE INDEX IF NOT EXISTS idx_recipe_component_recipe_id
    ON production.prod_recipe_component(recipe_id);

-- Reporting: find recipes that use a specific fiber
CREATE INDEX IF NOT EXISTS idx_recipe_component_fiber_id
    ON production.prod_recipe_component(fiber_id);

-- Certification and origin filtering for compliance reports
CREATE INDEX IF NOT EXISTS idx_recipe_component_certification
    ON production.prod_recipe_component(certification);

CREATE INDEX IF NOT EXISTS idx_recipe_component_origin
    ON production.prod_recipe_component(origin);
