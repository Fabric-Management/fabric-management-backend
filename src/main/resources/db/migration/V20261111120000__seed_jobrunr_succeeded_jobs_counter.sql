-- JobRunr's own v009 inserts the cluster-wide succeeded-jobs counter into jobrunr_metadata.
-- V20260603083000 pre-creates the JobRunr tables and marks v009 as installed (skip-create), but
-- never inserted this row. Without it every DeleteSucceededJobsTask run updates zero rows; after
-- five such storage exceptions (about 75 s after start) JobRunr shuts its BackgroundJobServer
-- down: no background job runs any more and /actuator/health reports DOWN. A database that
-- JobRunr initialised itself before skip-create may already hold the row, hence ON CONFLICT.
-- Values as in JobRunr 7.5.3 org/jobrunr/storage/sql/common/migrations/v009.
INSERT INTO public.jobrunr_metadata (id, name, owner, value, createdat, updatedat)
VALUES ('succeeded-jobs-counter-cluster', 'succeeded-jobs-counter', 'cluster', '0', now(), now())
ON CONFLICT (id) DO NOTHING;
