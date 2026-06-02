-- T1: fabric_app runtime yetkileri. Owner: fabric_owner. DDL grant'i YOK.
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
  -- GUARD: Eğer fabric_app rolü yoksa (ör. unit/integration testleri), işlemi atla.
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    RAISE NOTICE 'fabric_app yok — grant atlanıyor (test/CI ortamı)';
    RETURN;
  END IF;

  FOREACH s IN ARRAY schemas LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO fabric_app', s);
    EXECUTE format(
      'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO fabric_app', s);
    EXECUTE format(
      'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO fabric_app', s);

    -- D2: FOR ROLE yok → mevcut rolün (Flyway-owner) gelecekte oluşturacağı nesnelere uygulanır
    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES IN SCHEMA %I '
      'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fabric_app', s);
    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES IN SCHEMA %I '
      'GRANT USAGE, SELECT ON SEQUENCES TO fabric_app', s);
  END LOOP;

  -- D1: public.event_publication — dar grant, ON ALL TABLES değil
  EXECUTE 'GRANT USAGE ON SCHEMA public TO fabric_app';
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.event_publication TO fabric_app';
  -- public'e CREATE veya ON ALL TABLES VERME.

  -- Flyway history tablosuna app erişimi OLMASIN
  EXECUTE 'REVOKE ALL ON TABLE common_tenant.flyway_schema_history FROM fabric_app';

END $$;
