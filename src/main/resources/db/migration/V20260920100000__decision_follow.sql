-- DECISION-FOLLOW-1: durable, source-attributed follow facts and user suppression.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS flowboard.decision_follow (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL,
    user_id UUID NOT NULL,
    source TEXT NOT NULL,
    source_ref UUID,
    CONSTRAINT uq_decision_follow_reason UNIQUE (tenant_id, case_id, user_id, source),
    CONSTRAINT fk_decision_follow_case FOREIGN KEY (tenant_id, case_id)
        REFERENCES sales_ord.order_cover_case (tenant_id, id),
    CONSTRAINT fk_decision_follow_user FOREIGN KEY (tenant_id, user_id)
        REFERENCES common_user.common_user (tenant_id, id),
    CONSTRAINT chk_decision_follow_source
        CHECK (source IN ('SETTLED', 'ASSIGNED', 'OPENED', 'EXPLICIT')),
    CONSTRAINT chk_decision_follow_human
        CHECK (user_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE INDEX IF NOT EXISTS idx_decision_follow_user_case
    ON flowboard.decision_follow (tenant_id, user_id, case_id);

CREATE TABLE IF NOT EXISTS flowboard.decision_follow_suppression (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL,
    user_id UUID NOT NULL,
    suppressed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_decision_follow_suppression UNIQUE (tenant_id, case_id, user_id),
    CONSTRAINT fk_decision_follow_suppression_case FOREIGN KEY (tenant_id, case_id)
        REFERENCES sales_ord.order_cover_case (tenant_id, id),
    CONSTRAINT fk_decision_follow_suppression_user FOREIGN KEY (tenant_id, user_id)
        REFERENCES common_user.common_user (tenant_id, id),
    CONSTRAINT chk_decision_follow_suppression_human
        CHECK (user_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

ALTER TABLE flowboard.decision_follow ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.decision_follow FORCE ROW LEVEL SECURITY;
ALTER TABLE flowboard.decision_follow_suppression ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.decision_follow_suppression FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON flowboard.decision_follow FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
CREATE POLICY rls_tenant_isolation ON flowboard.decision_follow_suppression FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE FUNCTION flowboard.reject_decision_follow_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper)
        OR current_user = 'fabric_system'
        OR EXISTS (
            SELECT 1 FROM pg_roles
            WHERE rolname = 'fabric_system' AND pg_has_role(current_user, oid, 'MEMBER'))
    INTO trusted_purge_role;
    IF TG_OP = 'DELETE' AND trusted_purge_role
        AND current_setting('app.decision_follow_purge_tenant', true) = OLD.tenant_id::text THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Decision follow reasons are immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER decision_follow_immutable
    BEFORE UPDATE OR DELETE ON flowboard.decision_follow
    FOR EACH ROW EXECUTE FUNCTION flowboard.reject_decision_follow_mutation();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT ON flowboard.decision_follow TO fabric_app;
        REVOKE UPDATE, DELETE ON flowboard.decision_follow FROM fabric_app;
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON flowboard.decision_follow_suppression TO fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, DELETE ON flowboard.decision_follow TO fabric_system;
        REVOKE UPDATE ON flowboard.decision_follow FROM fabric_system;
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON flowboard.decision_follow_suppression TO fabric_system;
    END IF;
END $$;
