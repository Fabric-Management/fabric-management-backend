-- ============================================================================
-- FIBER-CATALOG-1 (production/playground): initial-provisioning ledger for playground fibre
-- fixtures.
-- ============================================================================
-- playground_fixture_run: one row per tenant, written by the trusted initial-provisioning flow
-- (legacy PLAYGROUND tenant creation or a register-first PLAYGROUND-intent signup). Without a
-- PENDING row nothing can install the fixtures; COMPLETED makes every later call a no-op.
-- playground_fixture_item: stable per-tenant fixture keys -> private row ids, so a retry of the
-- same provisioning repairs missing rows individually and never duplicates them.
-- Both tables are demo data: tenant purge / go-real deletes them with the tenant's fixtures.
-- ============================================================================

CREATE TABLE IF NOT EXISTS production.playground_fixture_run (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    uid          VARCHAR(100) NOT NULL,
    origin       VARCHAR(40)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   UUID,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_playground_fixture_run PRIMARY KEY (id),
    CONSTRAINT uq_playground_fixture_run_tenant UNIQUE (tenant_id),
    CONSTRAINT uq_playground_fixture_run_uid UNIQUE (uid),
    CONSTRAINT chk_playground_fixture_run_origin
        CHECK (origin IN ('LEGACY_PLAYGROUND_CREATION', 'REGISTER_FIRST_SIGNUP')),
    CONSTRAINT chk_playground_fixture_run_status
        CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_playground_fixture_run_completed
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS production.playground_fixture_item (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    uid         VARCHAR(100) NOT NULL,
    fixture_key VARCHAR(80)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   UUID         NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_playground_fixture_item PRIMARY KEY (id),
    CONSTRAINT uq_playground_fixture_item_key UNIQUE (tenant_id, fixture_key),
    CONSTRAINT uq_playground_fixture_item_uid UNIQUE (uid)
);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['production.playground_fixture_run', 'production.playground_fixture_item'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;
    -- DB-PRIV-2 closed table defaults: MUTABLE class grants for both runtime roles.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON production.playground_fixture_run, production.playground_fixture_item
            TO fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON production.playground_fixture_run, production.playground_fixture_item
            TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE production.playground_fixture_run IS
    'FIBER-CATALOG-1: trusted initial-provisioning marker for playground fibre fixtures.';
COMMENT ON TABLE production.playground_fixture_item IS
    'FIBER-CATALOG-1: stable playground fixture key -> private row id, for idempotent repair.';
