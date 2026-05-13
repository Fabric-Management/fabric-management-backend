-- =============================================================================
-- V20260326090000__create_inheritance_rule_schema.sql
-- Phase 5: Tenant-specific attribute inheritance rule overrides
-- =============================================================================

CREATE TABLE IF NOT EXISTS production.inheritance_rule_schema (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid             VARCHAR(26) NOT NULL UNIQUE,
    tenant_id       UUID NOT NULL,
    source_type     VARCHAR(30) NOT NULL,
    target_type     VARCHAR(30) NOT NULL,
    rules           JSONB NOT NULL DEFAULT '[]',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ
);

-- Unique constraint: one active rule per tenant + source + target combination
ALTER TABLE production.inheritance_rule_schema
    ADD CONSTRAINT uk_inheritance_tenant_src_tgt UNIQUE (tenant_id, source_type, target_type);

-- Index for fast lookup by tenant
CREATE INDEX IF NOT EXISTS idx_inheritance_rule_tenant ON production.inheritance_rule_schema (tenant_id);

-- Index for the most common query pattern
CREATE INDEX IF NOT EXISTS idx_inheritance_rule_lookup
    ON production.inheritance_rule_schema (tenant_id, source_type, target_type)
    WHERE is_active = TRUE;

-- RLS policy (consistent with other production tables)
ALTER TABLE production.inheritance_rule_schema ENABLE ROW LEVEL SECURITY;

CREATE POLICY inheritance_rule_schema_rls ON production.inheritance_rule_schema
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID);
