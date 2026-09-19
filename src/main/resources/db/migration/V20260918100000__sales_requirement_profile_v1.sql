-- SALES-REQ-1: typed current profile plus append-only history; legacy lines remain unprofiled.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_order_line_tenant_id
    ON sales_ord.sales_order_line (tenant_id, id);

ALTER TABLE sales_ord.sales_order_line
    ADD COLUMN IF NOT EXISTS requirement_profile_id UUID,
    ADD COLUMN IF NOT EXISTS requirement_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS requirement_profile_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requirement_profile_snapshot JSONB;

ALTER TABLE sales_ord.sales_order_line
    ADD CONSTRAINT chk_sales_line_requirement_profile_all_or_none CHECK (
        (requirement_profile_id IS NULL
            AND requirement_profile_version IS NULL
            AND requirement_profile_fingerprint IS NULL
            AND requirement_profile_snapshot IS NULL)
        OR
        (requirement_profile_id IS NOT NULL
            AND requirement_profile_version IS NOT NULL
            AND requirement_profile_version > 0
            AND requirement_profile_fingerprint IS NOT NULL
            AND requirement_profile_fingerprint ~ '^[0-9a-f]{64}$'
            AND requirement_profile_snapshot IS NOT NULL
            AND jsonb_typeof(requirement_profile_snapshot) = 'object')
    );

CREATE TABLE IF NOT EXISTS sales_ord.requirement_profile_version (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    profile_id UUID NOT NULL,
    profile_version INTEGER NOT NULL CHECK (profile_version > 0),
    sales_order_line_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    CONSTRAINT uq_requirement_profile_version
        UNIQUE (tenant_id, profile_id, profile_version),
    CONSTRAINT uq_requirement_profile_line_version
        UNIQUE (tenant_id, sales_order_line_id, profile_id, profile_version),
    CONSTRAINT fk_requirement_profile_line
        FOREIGN KEY (tenant_id, sales_order_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_requirement_profile_line
    ON sales_ord.requirement_profile_version (tenant_id, sales_order_line_id, profile_version);
CREATE INDEX IF NOT EXISTS idx_requirement_profile_lookup
    ON sales_ord.requirement_profile_version (tenant_id, profile_id, profile_version);

ALTER TABLE sales_ord.sales_order_line
    ADD CONSTRAINT fk_sales_line_current_requirement_profile
    FOREIGN KEY (tenant_id, id, requirement_profile_id, requirement_profile_version)
    REFERENCES sales_ord.requirement_profile_version
        (tenant_id, sales_order_line_id, profile_id, profile_version)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE sales_ord.requirement_profile_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.requirement_profile_version FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.requirement_profile_version FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

CREATE FUNCTION sales_ord.reject_requirement_profile_history_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper)
        OR current_user = 'fabric_system'
        OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system'
            AND pg_has_role(current_user, oid, 'MEMBER'))
    INTO trusted_purge_role;
    IF TG_OP = 'DELETE' AND trusted_purge_role
        AND current_setting('app.requirement_profile_purge_tenant', true) = OLD.tenant_id::TEXT THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Requirement-profile history is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER requirement_profile_history_immutable
    BEFORE UPDATE OR DELETE ON sales_ord.requirement_profile_version
    FOR EACH ROW EXECUTE FUNCTION sales_ord.reject_requirement_profile_history_mutation();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT ON sales_ord.requirement_profile_version TO fabric_app;
        REVOKE UPDATE, DELETE ON sales_ord.requirement_profile_version FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, DELETE ON sales_ord.requirement_profile_version TO fabric_system;
        REVOKE UPDATE ON sales_ord.requirement_profile_version FROM fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.requirement_profile_version IS
    'Append-only resolved requirement profile versions; production decisions pin profile_id + profile_version.';
