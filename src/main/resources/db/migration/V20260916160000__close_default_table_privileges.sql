-- DB-PRIV-2: close table defaults; subsequent migrations must grant explicitly.
-- Scoped to the executing owner. Existing tables and sequence defaults are unchanged.
DO $$
DECLARE
  s text;
  r text;
  schemas text[] := ARRAY[
    'common_tenant','common_company','common_user','common_auth',
    'common_communication','common_audit','common_policy','common_ai',
    'common_approval','production','human','finance','sales_ord',
    'logistics','procurement','costing','sales','i18n','notification',
    'flowboard','iwm','common_infrastructure'
  ];
BEGIN
  FOREACH r IN ARRAY ARRAY['fabric_app','fabric_system'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      RAISE NOTICE '% missing — default-privilege revoke skipped (test/CI environment)', r;
      CONTINUE;
    END IF;
    FOREACH s IN ARRAY schemas LOOP
      EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES FROM %I', s, r);
    END LOOP;
  END LOOP;
END $$;
