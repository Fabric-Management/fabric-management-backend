#!/bin/bash
set -e

# Bu betik docker-compose'da postgres servisi ayağa kalktığında çalışır.
# fabric_app rolünü container içinde oluşturur.
# Not: fabric_owner rolü, postgres imajı tarafından POSTGRES_USER üzerinden oluşturulduğundan
# container ortamında otomatik olarak SUPERUSER'dır. Bu da dolaylı yoldan FORCE RLS'i bypass eder.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
      CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS
        PASSWORD '${POSTGRES_APP_PASSWORD:-app_dev_2026}';
    END IF;

    -- fabric_system: runtime system operations (cross-tenant queries, tenant creation)
    -- BYPASSRLS but no DDL — separate from fabric_owner (migration-only)
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
      CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS
        PASSWORD '${POSTGRES_SYSTEM_PASSWORD:-system_dev_2026}';
    END IF;
  END \$\$;
EOSQL
