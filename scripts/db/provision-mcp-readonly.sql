-- =====================================================================
-- LOCAL DEVELOPMENT ONLY. Never run against staging or production.
--
-- Provisions fabric_mcp_ro: a read-only login used by the Postgres MCP
-- server so an AI assistant can inspect the local database.
--
-- Design:
--   * NOSUPERUSER + NOBYPASSRLS: sees exactly what fabric_app sees under
--     FORCE ROW LEVEL SECURITY. Every RLS policy in this schema has no
--     TO clause (applies to PUBLIC), so this role is bound by the same
--     tenant predicates as the application.
--   * SELECT-only grants: write safety does not depend on the MCP
--     server's own read-only mode. Even if that layer were bypassed, the
--     role holds no INSERT/UPDATE/DELETE/DDL privilege.
--   * Not a member of any role (NOINHERIT, no memberships), so it cannot
--     pick up fabric_app / fabric_system / fabric_owner privileges.
--   * Grants are DERIVED from the live catalogue (every non-system
--     schema), not from a hand-maintained list. Re-run this script after
--     a migration adds a schema or table; it is idempotent.
--   * Deliberately NOT a Flyway migration and does NOT use ALTER DEFAULT
--     PRIVILEGES, so it leaves no trace in migrations or pg_default_acl.
--
-- Run as the owner role (fabric_owner) from the repo root:
--   docker exec -i fabric-postgres psql -v ON_ERROR_STOP=1 \
--     -U fabric_owner -d fabric_management \
--     -v mcp_pw='<password>' -v tenant_id='<tenant uuid>' \
--     < fabric-management-backend/scripts/db/provision-mcp-readonly.sql
-- =====================================================================

\if :{?mcp_pw}
\else
  \echo 'ERROR: pass -v mcp_pw=...'
  \quit
\endif
\if :{?tenant_id}
\else
  \echo 'ERROR: pass -v tenant_id=<uuid> (the tenant the MCP session reads as)'
  \quit
\endif

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_mcp_ro') THEN
    CREATE ROLE fabric_mcp_ro LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
      NOBYPASSRLS NOINHERIT NOREPLICATION CONNECTION LIMIT 3;
  END IF;
END $$;

ALTER ROLE fabric_mcp_ro WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOBYPASSRLS NOINHERIT NOREPLICATION CONNECTION LIMIT 3
  PASSWORD :'mcp_pw';

-- Session defaults applied on every login.
ALTER ROLE fabric_mcp_ro SET default_transaction_read_only = on;
ALTER ROLE fabric_mcp_ro SET statement_timeout = '15s';
ALTER ROLE fabric_mcp_ro SET idle_in_transaction_session_timeout = '30s';
ALTER ROLE fabric_mcp_ro SET app.current_tenant = :'tenant_id';

GRANT CONNECT ON DATABASE fabric_management TO fabric_mcp_ro;

-- Derive grants from the catalogue: every non-system schema.
DO $$
DECLARE
  s text;
  m record;
BEGIN
  -- Drop any role memberships that may have crept in.
  FOR m IN
    SELECT r.rolname
    FROM pg_auth_members am
    JOIN pg_roles r ON r.oid = am.roleid
    WHERE am.member = (SELECT oid FROM pg_roles WHERE rolname = 'fabric_mcp_ro')
  LOOP
    EXECUTE format('REVOKE %I FROM fabric_mcp_ro', m.rolname);
  END LOOP;

  FOR s IN
    SELECT nspname FROM pg_namespace
    WHERE nspname NOT LIKE 'pg\_%'
      AND nspname <> 'information_schema'
  LOOP
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM fabric_mcp_ro', s);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO fabric_mcp_ro', s);
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO fabric_mcp_ro', s);
  END LOOP;
END $$;

-- Self-check: fail loudly if the role is not what we intend.
DO $$
DECLARE
  r pg_roles%ROWTYPE;
  bad int;
BEGIN
  SELECT * INTO r FROM pg_roles WHERE rolname = 'fabric_mcp_ro';
  IF r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb THEN
    RAISE EXCEPTION 'fabric_mcp_ro has an elevated attribute';
  END IF;

  SELECT count(*) INTO bad
  FROM information_schema.role_table_grants
  WHERE grantee = 'fabric_mcp_ro' AND privilege_type <> 'SELECT';
  IF bad > 0 THEN
    RAISE EXCEPTION 'fabric_mcp_ro holds % non-SELECT table privileges', bad;
  END IF;

  IF EXISTS (SELECT 1 FROM pg_auth_members
             WHERE member = r.oid) THEN
    RAISE EXCEPTION 'fabric_mcp_ro is a member of another role';
  END IF;

  RAISE NOTICE 'fabric_mcp_ro OK: read-only, RLS-bound, no memberships';
END $$;
