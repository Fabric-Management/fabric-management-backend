-- Reset fabric_management database for local development.
-- Run: make db-reset-local  (or: psql -U postgres -d postgres -f scripts/db-reset-local.sql)
-- Then: make run

\connect postgres;

-- fabric_owner, fabric_app ve fabric_system rollerini hazırla
DO $$
BEGIN
  -- Greenfield: fabric_user varsa rename et
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_user') THEN
    ALTER ROLE fabric_user RENAME TO fabric_owner;
    ALTER ROLE fabric_owner WITH BYPASSRLS;
  -- Sıfır ortam: fabric_owner yoksa oluştur
  ELSIF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_owner') THEN
    CREATE ROLE fabric_owner LOGIN NOSUPERUSER CREATEDB BYPASSRLS PASSWORD 'owner_dev_2026';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_dev_2026';
  END IF;

  -- fabric_system: runtime system operations (BYPASSRLS, no DDL)
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_dev_2026';
  END IF;
END $$;

-- Parolaları local dev değerlerine sabitle
ALTER ROLE fabric_owner  WITH PASSWORD 'owner_dev_2026';
ALTER ROLE fabric_app    WITH PASSWORD 'app_dev_2026';
ALTER ROLE fabric_system WITH PASSWORD 'system_dev_2026';

SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'fabric_management' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS fabric_management;

CREATE DATABASE fabric_management
  OWNER fabric_owner
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8'
  TEMPLATE template0;

\echo 'Database fabric_management has been reset. Run: make run'

