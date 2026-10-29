-- Pause signup/onboarding/module-selection traffic before rollback.
-- Preserve evidence, selected subscriptions and paid/cancelled history. Do not drop these columns
-- or reinstate registration-time learning deadlines. Prefer a forward application correction.
REVOKE ALL ON FUNCTION common_company.repair_playground_evaluation_packages() FROM PUBLIC;
-- Older application versions do not understand unclocked learning access and must not serve traffic
-- until a compatible forward fix or an operator-reviewed entitlement reconciliation is deployed.
