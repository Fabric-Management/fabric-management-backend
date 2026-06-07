CREATE TABLE IF NOT EXISTS public.processed_event (
    event_id     UUID         NOT NULL,
    listener_id  VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, listener_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_event_date 
    ON public.processed_event(processed_at);

COMMENT ON TABLE public.processed_event 
    IS 'Idempotency dedup for at-least-once event delivery (E2)';
