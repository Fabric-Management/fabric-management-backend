-- T4: fabric_system runtime privileges.
-- fabric_system has BYPASSRLS for cross-tenant operations (schedulers, auth login, tenant creation).
-- Same DML grants as fabric_app, but NO DDL capability.
DO $$
DECLARE
  s text;
  schemas text[] := ARRAY[
    'common_tenant','common_company','common_user','common_auth',
    'common_communication','common_audit','common_policy','common_ai',
    'common_approval','production','human','finance','sales_ord',
    'logistics','procurement','costing','sales','i18n','notification',
    'flowboard','iwm','common_infrastructure'
  ];
BEGIN
  -- GUARD: If fabric_system role doesn't exist (e.g. unit/integration tests), skip.
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    RAISE NOTICE 'fabric_system yok — grant atlanıyor (test/CI ortamı)';
    RETURN;
  END IF;

  FOREACH s IN ARRAY schemas LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO fabric_system', s);
    EXECUTE format(
      'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO fabric_system', s);
    EXECUTE format(
      'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO fabric_system', s);

    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES IN SCHEMA %I '
      'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fabric_system', s);
    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES IN SCHEMA %I '
      'GRANT USAGE, SELECT ON SEQUENCES TO fabric_system', s);
  END LOOP;

  -- public schema: event_publication + JobRunr tables
  EXECUTE 'GRANT USAGE ON SCHEMA public TO fabric_system';
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.event_publication TO fabric_system';

  -- Flyway history — no access for runtime roles
  EXECUTE 'REVOKE ALL ON TABLE common_tenant.flyway_schema_history FROM fabric_system';

END $$;
