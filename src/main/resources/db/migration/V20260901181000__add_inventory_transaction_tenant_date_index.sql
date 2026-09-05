-- FAILURE RUNBOOK (mandatory for this first non-transactional Flyway migration):
-- 1. If the new replica fails on V20260901181000, leave the old replicas serving.
-- 2. With the owner-configured production migration job/CLI and the application's production
--    datasource (never the Maven plugin's localhost defaults), run Flyway info and validate.
--    Continue only when V20260901181000 is the sole failure.
-- 3. Run Flyway repair and inspect its output. It must report exactly one removed failed row,
--    with no checksum/description/type realignment and no missing-migration deletion. Otherwise
--    stop and escalate.
-- 4. Redeploy. The schema-qualified DROP below removes an INVALID remnant before retrying.
-- 5. Run validate again, then run on the production connection:
--      SELECT indisvalid
--      FROM pg_index
--      WHERE indexrelid = 'production.idx_inv_txn_tenant_date'::regclass;
--    The result must be true. No manual DROP INDEX is needed or permitted.
--
-- POST-ROLLOUT CONVERGENCE RUNBOOK (mandatory; the deploy is unfinished until it passes):
-- After the last pre-1E replica has terminated, restart one 1E replica so the convergent blank
-- source-designation runner performs a final pass. Then, as the migration role with tenant RLS
-- bypassed, run this read-only verification query:
--      SELECT tenant_id, count(*)
--      FROM production.prod_yarn_article
--      WHERE status IN ('DRAFT', 'ACTIVE')
--        AND source_designation IS NOT NULL
--        AND btrim(source_designation, E' \t\n\r\f\x0B') = ''
--      GROUP BY tenant_id;
-- The trim set is exactly ASCII space, tab, LF, CR, FF and vertical tab. It is a strict subset
-- of Character.isWhitespace, so this check can under-report but cannot flag a value I32 accepts.
-- If any row is returned, restart one 1E replica again and repeat the query. Completion requires
-- zero rows; operators must not mutate article data with SQL.

DROP INDEX CONCURRENTLY IF EXISTS production.idx_inv_txn_tenant_date;

CREATE INDEX CONCURRENTLY idx_inv_txn_tenant_date
    ON production.production_execution_inventory_transaction (tenant_id, transaction_date);
