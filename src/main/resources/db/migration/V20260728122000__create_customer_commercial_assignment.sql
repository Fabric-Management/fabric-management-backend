CREATE TABLE IF NOT EXISTS sales.customer_commercial_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    customer_id UUID NOT NULL,
    representative_id UUID NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    source VARCHAR(40) NOT NULL,
    decided_by_type VARCHAR(10) NOT NULL,
    decided_by_user_id UUID,
    decided_by_system_code VARCHAR(60),
    closed_by_type VARCHAR(10),
    closed_by_user_id UUID,
    closed_by_system_code VARCHAR(60),
    closure_reason VARCHAR(40),
    policy_version VARCHAR(60) NOT NULL,
    supersedes_assignment_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_commercial_assignment_source
        CHECK (source IN (
            'ACQUISITION',
            'MANUAL',
            'TRIAGE_RESOLUTION',
            'BACKFILL_ACQUIRER',
            'BACKFILL_SOLE_TEAM_MEMBER',
            'SYSTEM_POLICY'
        )),
    CONSTRAINT chk_commercial_assignment_decided_actor
        CHECK (
            (decided_by_type = 'USER'
                AND decided_by_user_id IS NOT NULL
                AND decided_by_system_code IS NULL)
            OR
            (decided_by_type = 'SYSTEM'
                AND decided_by_user_id IS NULL
                AND NULLIF(BTRIM(decided_by_system_code), '') IS NOT NULL)
        ),
    CONSTRAINT chk_commercial_assignment_closed_actor
        CHECK (
            (closed_by_type IS NULL
                AND closed_by_user_id IS NULL
                AND closed_by_system_code IS NULL)
            OR
            (closed_by_type = 'USER'
                AND closed_by_user_id IS NOT NULL
                AND closed_by_system_code IS NULL)
            OR
            (closed_by_type = 'SYSTEM'
                AND closed_by_user_id IS NULL
                AND NULLIF(BTRIM(closed_by_system_code), '') IS NOT NULL)
        ),
    CONSTRAINT chk_commercial_assignment_state
        CHECK (
            (valid_to IS NULL
                AND closure_reason IS NULL
                AND closed_by_type IS NULL
                AND closed_by_user_id IS NULL
                AND closed_by_system_code IS NULL)
            OR
            (valid_to IS NOT NULL
                AND valid_to >= valid_from
                AND closure_reason IS NOT NULL
                AND closure_reason IN ('SUPERSEDED', 'REPRESENTATIVE_DEACTIVATED')
                AND closed_by_type IS NOT NULL)
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_commercial_assignment_open
    ON sales.customer_commercial_assignment (tenant_id, customer_id)
    WHERE valid_to IS NULL;

CREATE INDEX IF NOT EXISTS idx_customer_commercial_assignment_history
    ON sales.customer_commercial_assignment
        (tenant_id, customer_id, valid_from DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_customer_commercial_assignment_representative
    ON sales.customer_commercial_assignment
        (tenant_id, representative_id, valid_to);

COMMENT ON TABLE sales.customer_commercial_assignment IS
    'Retention-stable effective-dated commercial ownership; all cross-context ids are soft references.';

CREATE OR REPLACE FUNCTION sales.guard_customer_commercial_assignment_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    purge_tenant TEXT :=
        current_setting('app.customer_commercial_assignment_purge_tenant', true);
    trusted_purge_role BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        SELECT
            EXISTS (
                SELECT 1
                FROM pg_roles active_role
                WHERE active_role.rolname = current_user
                  AND active_role.rolsuper
            )
            OR current_user = 'fabric_system'
            OR EXISTS (
                SELECT 1
                FROM pg_roles system_role
                WHERE system_role.rolname = 'fabric_system'
                  AND pg_has_role(current_user, system_role.oid, 'MEMBER')
            )
        INTO trusted_purge_role;

        IF trusted_purge_role
           AND purge_tenant = OLD.tenant_id::TEXT THEN
            RETURN OLD;
        END IF;

        RAISE EXCEPTION
            'customer_commercial_assignment is retention-stable; row delete is forbidden'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.valid_to IS NOT NULL
       OR NEW.valid_to IS NULL
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.uid IS DISTINCT FROM OLD.uid
       OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
       OR NEW.representative_id IS DISTINCT FROM OLD.representative_id
       OR NEW.valid_from IS DISTINCT FROM OLD.valid_from
       OR NEW.source IS DISTINCT FROM OLD.source
       OR NEW.decided_by_type IS DISTINCT FROM OLD.decided_by_type
       OR NEW.decided_by_user_id IS DISTINCT FROM OLD.decided_by_user_id
       OR NEW.decided_by_system_code IS DISTINCT FROM OLD.decided_by_system_code
       OR NEW.policy_version IS DISTINCT FROM OLD.policy_version
       OR NEW.supersedes_assignment_id IS DISTINCT FROM OLD.supersedes_assignment_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.is_active IS DISTINCT FROM OLD.is_active
       OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at THEN
        RAISE EXCEPTION
            'customer_commercial_assignment only permits one-time closure'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_customer_commercial_assignment_mutation
    BEFORE UPDATE OR DELETE ON sales.customer_commercial_assignment
    FOR EACH ROW EXECUTE FUNCTION sales.guard_customer_commercial_assignment_mutation();

ALTER TABLE sales.customer_commercial_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.customer_commercial_assignment FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON sales.customer_commercial_assignment
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);
