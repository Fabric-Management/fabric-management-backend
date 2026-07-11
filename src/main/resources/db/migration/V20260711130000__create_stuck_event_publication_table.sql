CREATE TABLE IF NOT EXISTS public.stuck_event_publication (
    publication_id UUID         NOT NULL,
    event_type     VARCHAR(512) NOT NULL,
    listener_id    VARCHAR(512),
    tenant_id      UUID,
    first_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ,
    CONSTRAINT pk_stuck_event_publication PRIMARY KEY (publication_id)
);

CREATE INDEX IF NOT EXISTS idx_stuck_event_publication_unresolved
    ON public.stuck_event_publication(resolved_at);

COMMENT ON TABLE public.stuck_event_publication
    IS 'Dedup + resolution bookkeeping for the stuck-event monitor (EVENT-VISIBILITY-1 Slice A). Non-RLS, scheduler-written.';

DO $$
BEGIN
  IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'fabric_app') THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.stuck_event_publication TO fabric_app';
  END IF;
  IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'fabric_system') THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.stuck_event_publication TO fabric_system';
  END IF;
END $$;
