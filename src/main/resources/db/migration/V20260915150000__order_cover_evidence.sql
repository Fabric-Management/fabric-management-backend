-- FE-ARCH-5b-2: immutable derived evidence; case lifecycle and enrolment remain in 5b-3.
-- Transactional index build intentionally takes a sales-order write lock. Schedule a write-maintenance
-- window; fail fast on lock acquisition. A concurrent build requires a separate nontransactional rollout.
SET LOCAL lock_timeout = '5s';
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_order_evidence_tenant_id ON sales_ord.sales_order (tenant_id, id);

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_evidence_stream (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL, created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL, updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL, sales_order_id UUID NOT NULL,
    last_revision BIGINT NOT NULL DEFAULT 0 CHECK (last_revision >= 0),
    CONSTRAINT uq_cover_evidence_stream UNIQUE (tenant_id, case_id),
    CONSTRAINT uq_cover_evidence_stream_order UNIQUE (tenant_id, case_id, sales_order_id),
    CONSTRAINT fk_cover_evidence_stream_order FOREIGN KEY (tenant_id, sales_order_id)
        REFERENCES sales_ord.sales_order (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_cover_evidence_stream_order ON sales_ord.order_cover_evidence_stream
    (tenant_id, sales_order_id);
CREATE INDEX IF NOT EXISTS idx_cover_evidence_stream_rebuild ON sales_ord.order_cover_evidence_stream
    (tenant_id, created_at, id);

CREATE TABLE IF NOT EXISTS sales_ord.order_cover_evidence (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL, created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL, updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL, sales_order_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    order_version BIGINT NOT NULL CHECK (order_version >= 0),
    computed_at TIMESTAMPTZ NOT NULL, input_fingerprint VARCHAR(64) NOT NULL,
    rule_version VARCHAR(60) NOT NULL,
    requirements JSONB NOT NULL, inputs JSONB NOT NULL, lines JSONB NOT NULL,
    CONSTRAINT uq_cover_evidence_revision UNIQUE (tenant_id, case_id, revision),
    CONSTRAINT fk_cover_evidence_stream FOREIGN KEY (tenant_id, case_id, sales_order_id)
        REFERENCES sales_ord.order_cover_evidence_stream (tenant_id, case_id, sales_order_id),
    CONSTRAINT chk_cover_evidence_fingerprint CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_cover_evidence_payload CHECK
        (jsonb_typeof(requirements) = 'object' AND jsonb_typeof(inputs) = 'object'
         AND jsonb_typeof(lines) = 'array')
);
CREATE INDEX IF NOT EXISTS idx_cover_evidence_order ON sales_ord.order_cover_evidence (tenant_id, sales_order_id, id);
CREATE INDEX IF NOT EXISTS idx_cover_evidence_fingerprint ON sales_ord.order_cover_evidence
    (tenant_id, case_id, input_fingerprint);

ALTER TABLE sales_ord.order_cover_evidence_stream ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_evidence_stream FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_evidence_stream FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
ALTER TABLE sales_ord.order_cover_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.order_cover_evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.order_cover_evidence FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

CREATE FUNCTION sales_ord.reject_order_cover_evidence_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper)
        OR current_user = 'fabric_system'
        OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system'
            AND pg_has_role(current_user, oid, 'MEMBER'))
    INTO trusted_purge_role;
    IF TG_OP = 'DELETE' AND trusted_purge_role
        AND current_setting('app.order_cover_evidence_purge_tenant', true) = OLD.tenant_id::TEXT THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Order-cover evidence is append-only' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER order_cover_evidence_immutable
    BEFORE UPDATE OR DELETE ON sales_ord.order_cover_evidence
    FOR EACH ROW EXECUTE FUNCTION sales_ord.reject_order_cover_evidence_mutation();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE ON sales_ord.order_cover_evidence_stream TO fabric_app;
        GRANT SELECT, INSERT ON sales_ord.order_cover_evidence TO fabric_app;
        -- Earlier default-privilege migrations can grant UPDATE/DELETE on newly created tables.
        REVOKE UPDATE, DELETE ON sales_ord.order_cover_evidence FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.order_cover_evidence_stream TO fabric_system;
        GRANT SELECT, INSERT, DELETE ON sales_ord.order_cover_evidence TO fabric_system;
        REVOKE UPDATE ON sales_ord.order_cover_evidence FROM fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.order_cover_evidence IS
    'Append-only commercial evidence; retained with the sales order. Rebuild appends; no scheduled purge.';
COMMENT ON TABLE sales_ord.order_cover_evidence_stream IS
    'Internal evidence correlation and revision allocator, not a cover case. 5b-3 adds the real case FK before HTTP exposure.';
