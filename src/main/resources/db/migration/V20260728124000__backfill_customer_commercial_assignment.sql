WITH eligible_customer AS (
    SELECT
        partner.tenant_id,
        partner.id AS customer_id,
        partner.acquired_by_id,
        partner.customer_relationship_established_at,
        policy.mode_effective_at
    FROM common_company.common_trading_partner partner
    JOIN sales.ownership_policy policy
      ON policy.tenant_id = partner.tenant_id
    WHERE partner.partner_type IN ('CUSTOMER', 'BOTH')
      AND partner.is_active = TRUE
      AND policy.default_mode IN ('REQUIRED', 'OPTIONAL')
),
candidate AS (
    SELECT
        customer.*,
        CASE
            WHEN acquirer.id IS NOT NULL THEN acquirer.id
            WHEN team.member_count = 1 THEN team.sole_user_id
        END AS representative_id,
        CASE
            WHEN acquirer.id IS NOT NULL THEN 'BACKFILL_ACQUIRER'
            WHEN team.member_count = 1 THEN 'BACKFILL_SOLE_TEAM_MEMBER'
        END AS assignment_source
    FROM eligible_customer customer
    LEFT JOIN common_user.common_user acquirer
      ON acquirer.tenant_id = customer.tenant_id
     AND acquirer.id = customer.acquired_by_id
     AND acquirer.is_active = TRUE
     AND acquirer.user_type = 'INTERNAL'
    LEFT JOIN LATERAL (
        SELECT
            COUNT(DISTINCT member.user_id) AS member_count,
            (ARRAY_AGG(DISTINCT member.user_id ORDER BY member.user_id))[1] AS sole_user_id
        FROM sales.customer_account_team_member member
        JOIN common_user.common_user team_user
          ON team_user.tenant_id = member.tenant_id
         AND team_user.id = member.user_id
         AND team_user.is_active = TRUE
         AND team_user.user_type = 'INTERNAL'
        WHERE member.tenant_id = customer.tenant_id
          AND member.customer_id = customer.customer_id
          AND member.is_active = TRUE
    ) team ON TRUE
)
INSERT INTO sales.customer_commercial_assignment (
    id,
    tenant_id,
    uid,
    customer_id,
    representative_id,
    valid_from,
    source,
    decided_by_type,
    decided_by_system_code,
    policy_version,
    created_at,
    updated_at,
    is_active,
    version
)
SELECT
    gen_random_uuid(),
    candidate.tenant_id,
    'OWNERSHIP-BACKFILL-' || candidate.customer_id,
    candidate.customer_id,
    candidate.representative_id,
    GREATEST(
        candidate.customer_relationship_established_at,
        candidate.mode_effective_at
    ),
    candidate.assignment_source,
    'SYSTEM',
    'OWNERSHIP_POLICY',
    'OWNERSHIP_BACKFILL_V1',
    clock_timestamp(),
    clock_timestamp(),
    TRUE,
    0
FROM candidate
WHERE candidate.representative_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sales.customer_commercial_assignment existing
      WHERE existing.tenant_id = candidate.tenant_id
        AND existing.customer_id = candidate.customer_id
  );
