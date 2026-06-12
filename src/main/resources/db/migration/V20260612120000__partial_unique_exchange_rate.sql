-- Replace full unique constraint with partial unique index to support soft deletes
-- and determinism between MANUAL and ECB/TCMB rates for the same date.
-- V001 created the constraint; IF EXISTS guards fresh DBs where V001 may have been regenerated.

ALTER TABLE costing.exchange_rate_cache DROP CONSTRAINT IF EXISTS uq_exchange_rate_tenant_pair_date;

-- Also drop if it somehow exists as an index (defensive)
DROP INDEX IF EXISTS costing.uq_exchange_rate_tenant_pair_date;

CREATE UNIQUE INDEX uq_exchange_rate_tenant_pair_date
ON costing.exchange_rate_cache (tenant_id, base_currency, target_currency, rate_date)
WHERE is_active = true;
