-- Pre-create JobRunr 7.1.x tables (owner: fabric_owner via Flyway).
-- JobRunr auto-migration disabled via org.jobrunr.database.skip-create=true.
-- Bu tablolar tenant_id taşımaz → RLS-muaf (T6 allowlist adayı).

-- =========================================================================
-- TABLES
-- =========================================================================

CREATE TABLE IF NOT EXISTS public.jobrunr_migrations (
    id        character(36)        NOT NULL,
    script    character varying(64)  NOT NULL,
    installedon character varying(29) NOT NULL,
    CONSTRAINT jobrunr_migrations_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.jobrunr_jobs (
    id              character(36)          NOT NULL,
    version         integer                NOT NULL,
    jobasjson       text                   NOT NULL,
    jobsignature    character varying(512) NOT NULL,
    state           character varying(36)  NOT NULL,
    createdat       timestamp without time zone NOT NULL,
    updatedat       timestamp without time zone NOT NULL,
    scheduledat     timestamp without time zone,
    recurringjobid  character varying(128),
    CONSTRAINT jobrunr_jobs_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.jobrunr_recurring_jobs (
    id          character(128)  NOT NULL,
    version     integer         NOT NULL,
    jobasjson   text            NOT NULL,
    createdat   bigint          DEFAULT 0 NOT NULL,
    CONSTRAINT jobrunr_recurring_jobs_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.jobrunr_backgroundjobservers (
    id                       character(36)  NOT NULL,
    workerpoolsize           integer        NOT NULL,
    pollintervalinseconds    integer        NOT NULL,
    firstheartbeat           timestamp(6) without time zone NOT NULL,
    lastheartbeat            timestamp(6) without time zone NOT NULL,
    running                  integer        NOT NULL,
    systemtotalmemory        bigint         NOT NULL,
    systemfreememory          bigint         NOT NULL,
    systemcpuload            numeric(3,2)   NOT NULL,
    processmaxmemory         bigint         NOT NULL,
    processfreememory        bigint         NOT NULL,
    processallocatedmemory   bigint         NOT NULL,
    processcpuload           numeric(3,2)   NOT NULL,
    deletesucceededjobsafter character varying(32),
    permanentlydeletejobsafter character varying(32),
    name                     character varying(128),
    CONSTRAINT jobrunr_backgroundjobservers_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.jobrunr_metadata (
    id        character varying(156)  NOT NULL,
    name      character varying(92)   NOT NULL,
    owner     character varying(64)   NOT NULL,
    value     text                    NOT NULL,
    createdat timestamp without time zone NOT NULL,
    updatedat timestamp without time zone NOT NULL,
    CONSTRAINT jobrunr_metadata_pkey PRIMARY KEY (id)
);

-- =========================================================================
-- VIEW
-- =========================================================================

CREATE OR REPLACE VIEW public.jobrunr_jobs_stats AS
WITH job_stat_results AS (
    SELECT state, count(*) AS count
    FROM public.jobrunr_jobs
    GROUP BY ROLLUP(state)
)
SELECT
    COALESCE((SELECT count FROM job_stat_results WHERE state IS NULL), 0) AS total,
    COALESCE((SELECT count FROM job_stat_results WHERE state = 'SCHEDULED'), 0) AS scheduled,
    COALESCE((SELECT count FROM job_stat_results WHERE state = 'ENQUEUED'), 0) AS enqueued,
    COALESCE((SELECT count FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
    COALESCE((SELECT count FROM job_stat_results WHERE state = 'FAILED'), 0) AS failed,
    COALESCE((SELECT count FROM job_stat_results WHERE state = 'SUCCEEDED'), 0) AS succeeded,
    COALESCE((SELECT (jm.value::character(10))::numeric(10,0)
              FROM public.jobrunr_metadata jm
              WHERE jm.id = 'succeeded-jobs-counter-cluster'), 0) AS alltimesucceeded,
    COALESCE((SELECT count FROM job_stat_results WHERE state = 'DELETED'), 0) AS deleted,
    (SELECT count(*) FROM public.jobrunr_backgroundjobservers) AS nbrofbackgroundjobservers,
    (SELECT count(*) FROM public.jobrunr_recurring_jobs) AS nbrofrecurringjobs;

-- =========================================================================
-- INDEXES
-- =========================================================================

CREATE INDEX IF NOT EXISTS jobrunr_state_idx
    ON public.jobrunr_jobs USING btree (state);

CREATE INDEX IF NOT EXISTS jobrunr_jobs_state_updated_idx
    ON public.jobrunr_jobs USING btree (state, updatedat);

CREATE INDEX IF NOT EXISTS jobrunr_job_created_at_idx
    ON public.jobrunr_jobs USING btree (createdat);

CREATE INDEX IF NOT EXISTS jobrunr_job_scheduled_at_idx
    ON public.jobrunr_jobs USING btree (scheduledat);

CREATE INDEX IF NOT EXISTS jobrunr_job_signature_idx
    ON public.jobrunr_jobs USING btree (jobsignature);

CREATE INDEX IF NOT EXISTS jobrunr_job_rci_idx
    ON public.jobrunr_jobs USING btree (recurringjobid);

CREATE INDEX IF NOT EXISTS jobrunr_recurring_job_created_at_idx
    ON public.jobrunr_recurring_jobs USING btree (createdat);

CREATE INDEX IF NOT EXISTS jobrunr_bgjobsrvrs_fsthb_idx
    ON public.jobrunr_backgroundjobservers USING btree (firstheartbeat);

CREATE INDEX IF NOT EXISTS jobrunr_bgjobsrvrs_lsthb_idx
    ON public.jobrunr_backgroundjobservers USING btree (lastheartbeat);

-- =========================================================================
-- SEED JobRunr internal migrations tracker so it thinks migrations are done
-- =========================================================================

INSERT INTO public.jobrunr_migrations (id, script, installedon)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'v000__create_migrations_table',  NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000001', 'v001__create_job_table',          NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000002', 'v002__create_recurring_job_table', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000003', 'v003__create_background_job_server_table', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000004', 'v004__create_job_stats_view',     NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000005', 'v005__update_job_stats_view',     NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000006', 'v006__alter_table_jobs_add_recurringjobid', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000007', 'v007__alter_table_backgroundjobserver', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000008', 'v008__alter_table_jobs_increase_jobsignature', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000009', 'v009__change_jobrunr_job_counters_to_jobrunr_metadata', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000010', 'v010__change_job_stats',           NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000011', 'v011__alter_table_recurring_jobs_add_createdAt', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000012', 'v012__alter_table_background_job_servers_add_name', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000013', 'v013__alter_table_background_job_servers_add_delete_config', NOW()::varchar(29)),
    ('00000000-0000-0000-0000-000000000014', 'v014__alter_table_recurring_jobs_add_labels', NOW()::varchar(29))
-- ⚠️  BORÇ: JobRunr upgrade'inde (7.1.1 → 7.x) bu dosya + seed satırları 
--     güncellenmeli. skip-create: true yüzünden yeni migration'lar otomatik çalışmaz.
ON CONFLICT (id) DO NOTHING;

-- =========================================================================
-- NARROW DML GRANT — fabric_app gets DML only, NO CREATE
-- =========================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    RAISE NOTICE 'fabric_app yok — grant atlanıyor (test/CI ortamı)';
    RETURN;
  END IF;

  -- JobRunr tablolarına dar DML
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobrunr_migrations TO fabric_app';
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobrunr_jobs TO fabric_app';
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobrunr_recurring_jobs TO fabric_app';
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobrunr_backgroundjobservers TO fabric_app';
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobrunr_metadata TO fabric_app';
  EXECUTE 'GRANT SELECT ON public.jobrunr_jobs_stats TO fabric_app';
END $$;
