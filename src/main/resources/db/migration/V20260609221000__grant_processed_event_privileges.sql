-- Grant DML permissions on the processed_event table to the application and system roles
DO $$
BEGIN
  -- Grant to fabric_app
  IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'fabric_app') THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.processed_event TO fabric_app';
  END IF;

  -- Grant to fabric_system
  IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'fabric_system') THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.processed_event TO fabric_system';
  END IF;
END $$;
