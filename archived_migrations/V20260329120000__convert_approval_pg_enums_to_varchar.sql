-- PostgreSQL JDBC exposes native enum columns as VARCHAR in metadata; Hibernate 6 schema
-- validation then rejects @JdbcTypeCode(NAMED_ENUM). VARCHAR columns match @Enumerated(STRING)
-- and avoid "operator does not exist: enum = character varying" on parameterized queries.

-- ---------------------------------------------------------------------------
-- 1. approval_policy.required_for_level (policy_target_level)
-- ---------------------------------------------------------------------------
ALTER TABLE common_approval.approval_policy
    ALTER COLUMN required_for_level TYPE VARCHAR(32) USING required_for_level::text;

-- ---------------------------------------------------------------------------
-- 2. approval_request.status (approval_request_status)
-- ---------------------------------------------------------------------------
ALTER TABLE common_approval.approval_request
    ALTER COLUMN status DROP DEFAULT;
ALTER TABLE common_approval.approval_request
    ALTER COLUMN status TYPE VARCHAR(32) USING status::text;
ALTER TABLE common_approval.approval_request
    ALTER COLUMN status SET DEFAULT 'PENDING';

-- ---------------------------------------------------------------------------
-- 3. user_promotion_request (user_trust_level, promotion_*, trigger)
-- Partial index uq_promotion_pending_per_user predicates on status = 'PENDING';
-- that forces varchar = enum during ALTER TYPE unless the index is dropped first.
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS common_approval.uq_promotion_pending_per_user;

ALTER TABLE common_approval.user_promotion_request
    ALTER COLUMN from_level TYPE VARCHAR(32) USING from_level::text;
ALTER TABLE common_approval.user_promotion_request
    ALTER COLUMN to_level TYPE VARCHAR(32) USING to_level::text;
ALTER TABLE common_approval.user_promotion_request
    ALTER COLUMN status DROP DEFAULT;
ALTER TABLE common_approval.user_promotion_request
    ALTER COLUMN status TYPE VARCHAR(32) USING status::text;
ALTER TABLE common_approval.user_promotion_request
    ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE common_approval.user_promotion_request
    ALTER COLUMN triggered_by TYPE VARCHAR(32) USING triggered_by::text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_promotion_pending_per_user
    ON common_approval.user_promotion_request (tenant_id, user_id)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 4. common_user.trust_level (user_trust_level)
-- ---------------------------------------------------------------------------
ALTER TABLE common_user.common_user
    ALTER COLUMN trust_level DROP DEFAULT;
ALTER TABLE common_user.common_user
    ALTER COLUMN trust_level TYPE VARCHAR(32) USING trust_level::text;
ALTER TABLE common_user.common_user
    ALTER COLUMN trust_level SET DEFAULT 'PROBATION';
ALTER TABLE common_user.common_user
    ALTER COLUMN trust_level SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. Drop unused enum types (columns no longer reference them)
-- ---------------------------------------------------------------------------
DROP TYPE IF EXISTS common_approval.approval_request_status;
DROP TYPE IF EXISTS common_approval.policy_target_level;
DROP TYPE IF EXISTS common_approval.promotion_request_status;
DROP TYPE IF EXISTS common_approval.promotion_trigger_type;
DROP TYPE IF EXISTS common_auth.user_trust_level;
