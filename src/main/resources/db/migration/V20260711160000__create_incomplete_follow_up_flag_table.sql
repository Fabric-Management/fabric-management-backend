CREATE TABLE IF NOT EXISTS common_infrastructure.incomplete_follow_up_flag (
    id               uuid PRIMARY KEY,
    uid              varchar(100) UNIQUE,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    created_by       uuid,
    updated_by       uuid,
    tenant_id        uuid NOT NULL,
    is_active        boolean NOT NULL DEFAULT true,
    deleted_at       timestamptz,
    version          bigint NOT NULL DEFAULT 0,

    publication_id   uuid NOT NULL,
    event_type       varchar(512) NOT NULL,
    entity_type      varchar(64) NOT NULL,
    entity_id        uuid,
    entity_ref       varchar(128),
    summary          text NOT NULL,
    reference_type   varchar(64),
    reference_id     uuid,
    affected_user_id uuid,
    status           varchar(20) NOT NULL,
    resolved_at      timestamptz,

    CONSTRAINT uq_ifu_flag_tenant_publication UNIQUE (tenant_id, publication_id),
    CONSTRAINT chk_ifu_flag_status CHECK (status IN ('ACTIVE', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_ifu_flag_tenant_entity_status
    ON common_infrastructure.incomplete_follow_up_flag
        (tenant_id, entity_type, entity_id, status);

CREATE INDEX IF NOT EXISTS idx_ifu_flag_tenant_status
    ON common_infrastructure.incomplete_follow_up_flag (tenant_id, status);

ALTER TABLE common_infrastructure.incomplete_follow_up_flag ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_infrastructure.incomplete_follow_up_flag FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation
    ON common_infrastructure.incomplete_follow_up_flag;
CREATE POLICY rls_tenant_isolation
    ON common_infrastructure.incomplete_follow_up_flag
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE
        ON TABLE common_infrastructure.incomplete_follow_up_flag TO fabric_app;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE
        ON TABLE common_infrastructure.incomplete_follow_up_flag TO fabric_system;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;
