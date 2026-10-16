-- Tenant-owned static SVG artwork: at most four 512 KB slots per tenant, reused after removal.
-- Existing general settings saves never touch these slots.
CREATE TABLE IF NOT EXISTS common_tenant.tenant_brand_asset (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES common_tenant.common_tenant(id) ON DELETE CASCADE,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('LOGO', 'ICON', 'LOGO_DARK', 'ICON_DARK')),
    colour_mode VARCHAR(20) NOT NULL CHECK (colour_mode IN ('ORIGINAL', 'MONOCHROME')),
    file_name VARCHAR(255) NOT NULL,
    svg_content TEXT NOT NULL CHECK (octet_length(svg_content) BETWEEN 1 AND 524288)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_brand_asset_slot
    ON common_tenant.tenant_brand_asset (tenant_id, kind);
ALTER TABLE common_tenant.tenant_brand_asset ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_tenant.tenant_brand_asset FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON common_tenant.tenant_brand_asset FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE ON common_tenant.tenant_brand_asset TO fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON common_tenant.tenant_brand_asset TO fabric_system;
    END IF;
END $$;
