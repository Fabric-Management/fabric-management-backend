-- QC-RELEASE-1a follow-up: allow the system tenant-reset transaction to remove ledger rows
-- without weakening append-only behavior for normal application traffic.

CREATE OR REPLACE FUNCTION production.reject_quality_decision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    purge_tenant TEXT := current_setting('app.quality_decision_purge_tenant', true);
    trusted_purge_role BOOLEAN;
BEGIN
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

    IF TG_OP = 'DELETE'
       AND trusted_purge_role
       AND purge_tenant = OLD.tenant_id::TEXT THEN
        RETURN OLD;
    END IF;

    RAISE EXCEPTION '% is append-only; % is forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = '55000';
END;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        REVOKE UPDATE, DELETE ON TABLE
            production.quality_decision,
            production.quality_decision_unit
        FROM fabric_app;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        REVOKE UPDATE ON TABLE
            production.quality_decision,
            production.quality_decision_unit
        FROM fabric_system;
        GRANT DELETE ON TABLE
            production.quality_decision,
            production.quality_decision_unit
        TO fabric_system;
    END IF;
END $$;
