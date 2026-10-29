-- Evidence-preserving operational rollback. Pause signup/step traffic before application downgrade.
-- Do NOT drop Lead columns/history/journeys/receipts or restore an old database over captured data.
-- The old after-commit capture implementation cannot meet durable signup requirements: keep new
-- signup disabled until the corrected application is restored. Existing personal login can remain.
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    REVOKE INSERT, UPDATE, DELETE ON common_user.onboarding_journey, common_user.onboarding_operation,
      common_company.lead_profile_snapshot FROM fabric_app;
  END IF;
END $$;
-- Keep FK ON DELETE SET NULL on common_lead.trial_tenant_id and independent signup correlation.
-- Recovery is a reviewed forward migration; captured prospect evidence is not a rollback casualty.
