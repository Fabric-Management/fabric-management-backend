-- YARN-0C / twist-v1: populate the canonical TPM key without deleting historical TPI evidence.
-- The absence predicate makes this UPDATE idempotent and preserves already-canonical values.
UPDATE production.production_execution_batch
SET attributes =
    jsonb_set(
        attributes,
        '{yarn_twist_tpm}',
        to_jsonb(round((attributes ->> 'yarn_tpi')::numeric * 39.37, 2)),
        true
    )
WHERE product_type = 'YARN'
  AND attributes ? 'yarn_tpi'
  AND NOT attributes ? 'yarn_twist_tpm';

-- YARN-3B sunset guard — expected result after this migration: 0.
-- SELECT count(*)
-- FROM production.production_execution_batch
-- WHERE product_type = 'YARN'
--   AND attributes ? 'yarn_tpi'
--   AND NOT attributes ? 'yarn_twist_tpm';
