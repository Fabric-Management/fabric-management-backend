-- REG-ONB-05. Apply with signup/onboarding traffic paused; no billing activation.
ALTER TABLE common_company.common_os_definition
  ADD COLUMN evaluation_available boolean NOT NULL DEFAULT true;
-- The system catalogue is shared read-only. Existing tenant-owned definitions remain private
-- and are never generally offered evaluation options. Existing write policies stay tenant-scoped.
CREATE POLICY evaluation_catalogue_read ON common_company.common_os_definition
  FOR SELECT USING (tenant_id = '00000000-0000-0000-0000-000000000000'::uuid);
CREATE POLICY evaluation_feature_catalogue_read ON common_company.common_feature_catalog
  FOR SELECT USING (tenant_id = '00000000-0000-0000-0000-000000000000'::uuid);
ALTER TABLE common_company.common_subscription
  ADD COLUMN evaluation_managed boolean NOT NULL DEFAULT false,
  ADD COLUMN learning_access boolean NOT NULL DEFAULT false,
  ADD COLUMN evaluation_selection_source varchar(30);
ALTER TABLE common_company.common_subscription ADD CONSTRAINT ck_learning_evaluation
  CHECK (NOT learning_access OR (evaluation_managed AND status = 'TRIAL' AND trial_ends_at IS NULL));

-- Catalogue commands and catalogue maintenance share one gate, including inserts into the catalogue.
-- No new catalogue write privileges are granted to runtime roles.
CREATE FUNCTION common_company.lock_evaluation_catalogue() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(20261025, 130000);
  RETURN NULL;
END $$;
CREATE TRIGGER evaluation_catalogue_write_lock
  BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON common_company.common_os_definition
  FOR EACH STATEMENT EXECUTE FUNCTION common_company.lock_evaluation_catalogue();
REVOKE ALL ON FUNCTION common_company.lock_evaluation_catalogue() FROM PUBLIC;

-- REG-ONB-05b. The system catalogue: the reviewed, generally offered packages.
-- Installed by the normal migration path so a clean database completes a PLAYGROUND signup with no
-- test fixture. Idempotent and non-destructive: a package that already exists (matched by os_code or
-- uid) is left exactly as the operator maintains it, so re-running never overwrites a change.
-- Tiers and defaults mirror PricingTierValidator. included_modules lists the permission resources a
-- package opens (PackageAccessAdapter): a deselected package closes exactly those resources, and a
-- resource no package lists stays open. CustomOS is customer-specific and is deliberately not offered.
-- The migration role must bypass row-level security, as REG-ONB-01 already requires.
INSERT INTO common_company.common_os_definition
  (tenant_id, uid, os_code, os_name, os_type, description, included_modules, available_tiers,
   default_tier, is_active, evaluation_available)
VALUES
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-FabricOS', 'FabricOS', 'FabricOS', 'BASE', 'Base platform: sales, products, stock, procurement, production, quality, logistics and settings.',
    '["sales", "products", "fiber", "fabric", "colors", "logistics", "procurement", "quality", "flowboard", "production", "costing", "settings"]'::jsonb, '["Base"]'::jsonb, 'Base', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-YarnOS', 'YarnOS', 'YarnOS', 'FULL', 'Yarn stock management and supply-chain tracking.',
    '["yarn"]'::jsonb, '["Starter", "Professional", "Enterprise"]'::jsonb, 'Starter', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-LoomOS', 'LoomOS', 'LoomOS', 'FULL', 'Loom planning and weaving production tracking.',
    '[]'::jsonb, '["Starter", "Professional", "Enterprise"]'::jsonb, 'Starter', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-KnitOS', 'KnitOS', 'KnitOS', 'FULL', 'Knitting machine planning and pattern management.',
    '[]'::jsonb, '["Starter", "Professional", "Enterprise"]'::jsonb, 'Starter', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-DyeOS', 'DyeOS', 'DyeOS', 'FULL', 'Dye recipes, colour management and laboratory integration.',
    '[]'::jsonb, '["Starter", "Professional", "Enterprise"]'::jsonb, 'Starter', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-AnalyticsOS', 'AnalyticsOS', 'AnalyticsOS', 'FULL', 'Reporting, dashboards and data analysis.',
    '[]'::jsonb, '["Standard", "Advanced", "Enterprise"]'::jsonb, 'Standard', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-IntelligenceOS', 'IntelligenceOS', 'IntelligenceOS', 'FULL', 'AI-assisted forecasting and optimisation.',
    '[]'::jsonb, '["Professional", "Enterprise"]'::jsonb, 'Professional', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-EdgeOS', 'EdgeOS', 'EdgeOS', 'FULL', 'IoT device management and real-time monitoring.',
    '[]'::jsonb, '["Starter", "Professional", "Enterprise"]'::jsonb, 'Starter', true, true),
  ('00000000-0000-0000-0000-000000000000'::uuid, 'SYS-OS-AccountOS', 'AccountOS', 'AccountOS', 'FULL', 'Financial management, collections and cost tracking.',
    '["finance"]'::jsonb, '["Standard", "Professional", "Enterprise"]'::jsonb, 'Standard', true, true)
ON CONFLICT DO NOTHING;

-- Operator reconciliation records for package state the repair below cannot classify. Retained with
-- the Lead (no tenant_id/RLS, like onboarding_link_issue); runtime roles have no access.
CREATE TABLE IF NOT EXISTS common_company.evaluation_reconciliation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_id uuid NOT NULL REFERENCES common_company.common_lead(id) ON DELETE CASCADE,
  observed_tenant_id uuid NOT NULL,
  reason varchar(60) NOT NULL,
  details jsonb NOT NULL,
  observed_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_evaluation_reconciliation UNIQUE (lead_id, reason)
);
REVOKE ALL ON common_company.evaluation_reconciliation FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    REVOKE ALL ON common_company.evaluation_reconciliation FROM fabric_app;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    REVOKE ALL ON common_company.evaluation_reconciliation FROM fabric_system;
  END IF;
END $$;

-- Owner-only repair, also callable by the operator to prove idempotency. Never guesses a signup link.
CREATE FUNCTION common_company.repair_playground_evaluation_packages() RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
  prospect record;
  definition record;
  selected jsonb;
  selection_source text;
  repaired integer := 0;
BEGIN
  PERFORM pg_advisory_xact_lock(20261025, 130000);
  IF NOT EXISTS (SELECT 1 FROM common_company.common_os_definition
      WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid AND os_code = 'FabricOS' AND is_active AND deleted_at IS NULL AND evaluation_available)
      OR EXISTS (SELECT 1 FROM common_company.common_os_definition
        WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid AND is_active AND deleted_at IS NULL AND evaluation_available
          AND (default_tier IS NULL OR NOT COALESCE(available_tiers ? default_tier, false))) THEN
    -- Fresh databases may receive catalogue seeds after Flyway; there are no prospects to repair.
    IF EXISTS (SELECT 1 FROM common_user.onboarding_journey j JOIN common_tenant.common_tenant t ON t.id = j.tenant_id
        WHERE j.kind = 'COMPANY' AND j.signup_intent = 'PLAYGROUND' AND t.demo_mode AND t.status = 'TRIAL' AND t.is_active) THEN
      RAISE EXCEPTION 'Evaluation catalogue is missing FabricOS or has invalid default tiers';
    END IF;
    RETURN 0;
  END IF;

  FOR prospect IN
    SELECT t.id, j.id AS journey_id, j.accepted_steps, l.id AS lead_id, l.accepted_snapshot,
      (SELECT h.evidence->'selectedOS' FROM common_company.lead_profile_snapshot h
       WHERE h.lead_id = l.id AND h.source = 'STEP_ACCEPTED' AND h.step_id = 'PLATFORM_MODULES'
       ORDER BY h.revision DESC LIMIT 1) AS module_selection
    FROM common_tenant.common_tenant t
    JOIN common_user.onboarding_journey j ON j.tenant_id = t.id AND j.kind = 'COMPANY'
    JOIN common_company.common_lead l ON l.signup_id = j.signup_id AND l.signup_tenant_id = t.id
    WHERE j.signup_intent = 'PLAYGROUND' AND t.demo_mode AND t.status = 'TRIAL' AND t.is_active
      AND NOT EXISTS (SELECT 1 FROM common_company.common_subscription s WHERE s.tenant_id = t.id
        AND (s.evaluation_selection_source IS NOT NULL OR s.status IN ('ACTIVE', 'CANCELLED', 'SUSPENDED')
          OR s.deleted_at IS NOT NULL OR NOT s.is_active))
    FOR UPDATE OF t, j
  LOOP
    IF prospect.accepted_steps ? 'PLATFORM_MODULES' THEN
      -- Only an accepted modules command proves a choice. Raw/legacy selectedOS did not grant access.
      selected := prospect.module_selection;
      IF jsonb_typeof(selected) IS DISTINCT FROM 'array' OR NOT (selected ? 'FabricOS') THEN
        INSERT INTO common_company.evaluation_reconciliation(lead_id, observed_tenant_id, reason, details)
        VALUES (prospect.lead_id, prospect.id, 'ACCEPTED_SELECTION_UNRESOLVED',
          jsonb_build_object('journeyId', prospect.journey_id, 'acceptedSelection', selected))
        ON CONFLICT (lead_id, reason) DO UPDATE SET details = EXCLUDED.details, observed_at = now();
        CONTINUE;
      END IF;
      IF EXISTS (SELECT 1 FROM jsonb_array_elements_text(selected) chosen(code)
        WHERE NOT EXISTS (SELECT 1 FROM common_company.common_os_definition d
          WHERE d.tenant_id = '00000000-0000-0000-0000-000000000000'::uuid AND d.os_code = chosen.code AND d.is_active AND d.deleted_at IS NULL AND d.evaluation_available)) THEN
        INSERT INTO common_company.evaluation_reconciliation(lead_id, observed_tenant_id, reason, details)
        VALUES (prospect.lead_id, prospect.id, 'ACCEPTED_SELECTION_NOT_OFFERED',
          jsonb_build_object('journeyId', prospect.journey_id, 'acceptedSelection', selected))
        ON CONFLICT (lead_id, reason) DO UPDATE SET details = EXCLUDED.details, observed_at = now();
        CONTINUE;
      END IF;
      selection_source := 'OWNER';
    ELSE
      SELECT jsonb_agg(os_code ORDER BY os_code) INTO selected
      FROM common_company.common_os_definition
      WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid AND is_active AND deleted_at IS NULL AND evaluation_available;
      selection_source := 'REPAIRED_DEFAULT';
    END IF;

    FOR definition IN SELECT * FROM common_company.common_os_definition
      WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid AND is_active AND deleted_at IS NULL AND evaluation_available ORDER BY os_code
    LOOP
      IF NOT (selected ? definition.os_code) THEN CONTINUE; END IF;
      INSERT INTO common_company.common_subscription AS current_subscription
        (tenant_id, uid, os_code, os_name, status, pricing_tier, start_date,
         trial_ends_at, evaluation_managed, learning_access, evaluation_selection_source)
      VALUES (prospect.id, 'SUB-' || gen_random_uuid(), definition.os_code, definition.os_name,
        'TRIAL', definition.default_tier, now(), NULL, true, true, selection_source)
      ON CONFLICT (tenant_id, os_code) DO UPDATE
        SET status = 'TRIAL', pricing_tier = EXCLUDED.pricing_tier, trial_ends_at = NULL,
            expiry_date = NULL, evaluation_managed = true, learning_access = true,
            evaluation_selection_source = selection_source, updated_at = now(), version = current_subscription.version + 1;
    END LOOP;
    -- Keep rows and history for packages omitted by an evidenced owner command.
    UPDATE common_company.common_subscription
      SET status = 'SUSPENDED', evaluation_managed = true, learning_access = false,
          evaluation_selection_source = selection_source, updated_at = now(), version = version + 1
      WHERE tenant_id = prospect.id AND NOT (selected ? os_code) AND status IN ('TRIAL', 'EXPIRED');
    UPDATE common_tenant.common_tenant
      SET trial_started_at = NULL, trial_ends_at = NULL, updated_at = now(), version = version + 1
      WHERE id = prospect.id;
    UPDATE common_user.onboarding_journey
      SET command_version = command_version + 1, accepted_steps = accepted_steps - 'REVIEW',
          review_operation_id = NULL, updated_at = now(), version = version + 1
      WHERE id = prospect.journey_id;
    -- A later legacy profile patch could have retained an ignored selectedOS field. Restore the
    -- proven modules-command choice in a new revision, without rewriting any historic snapshot.
    IF selection_source = 'OWNER' AND prospect.accepted_snapshot->'selectedOS' IS DISTINCT FROM selected THEN
      WITH corrected AS (
        UPDATE common_company.common_lead SET accepted_revision = accepted_revision + 1,
          accepted_snapshot = jsonb_set(accepted_snapshot, '{selectedOS}', selected),
          updated_at = now(), version = version + 1
        WHERE id = prospect.lead_id RETURNING id, accepted_revision, accepted_snapshot
      )
      INSERT INTO common_company.lead_profile_snapshot(lead_id, revision, source, evidence)
        SELECT id, accepted_revision, 'EVALUATION_ACCESS_REPAIR', accepted_snapshot FROM corrected;
    END IF;
    repaired := repaired + 1;
  END LOOP;
  -- A prior cancellation/suspension is evidence against applying defaults. Preserve that set,
  -- while removing only premature clocks from its still-selected trial packages.
  WITH preserved AS (UPDATE common_company.common_subscription s
    SET status = 'TRIAL', learning_access = true, trial_ends_at = NULL, expiry_date = NULL,
        evaluation_managed = true, evaluation_selection_source = 'REPAIRED_PRESERVED',
        updated_at = now(), version = s.version + 1
    FROM common_user.onboarding_journey j, common_tenant.common_tenant t
    WHERE s.tenant_id = t.id AND j.tenant_id = t.id AND j.kind = 'COMPANY' AND j.signup_intent = 'PLAYGROUND'
      AND t.demo_mode AND t.status = 'TRIAL' AND t.is_active
      AND s.is_active AND s.deleted_at IS NULL AND s.evaluation_selection_source IS NULL
      -- An EXPIRED row is the old registration-time clock only when it carries that clock's footprint:
      -- a passed trial deadline and no paid expiry date. Anything else is reconciled, never guessed.
      AND (s.status = 'TRIAL' OR (s.status = 'EXPIRED' AND s.expiry_date IS NULL
        AND s.trial_ends_at IS NOT NULL AND s.trial_ends_at <= now()))
      AND NOT EXISTS (SELECT 1 FROM common_company.common_subscription paid WHERE paid.tenant_id = t.id AND paid.status = 'ACTIVE')
      AND EXISTS (SELECT 1 FROM common_company.common_subscription stopped WHERE stopped.tenant_id = t.id
        AND stopped.status IN ('CANCELLED', 'SUSPENDED'))
    RETURNING s.tenant_id)
  UPDATE common_user.onboarding_journey j
    SET command_version = command_version + 1, accepted_steps = accepted_steps - 'REVIEW',
        review_operation_id = NULL, updated_at = now(), version = version + 1
    WHERE j.kind = 'COMPANY' AND j.tenant_id IN (SELECT tenant_id FROM preserved);
  UPDATE common_tenant.common_tenant t SET trial_started_at = NULL, trial_ends_at = NULL,
      updated_at = now(), version = version + 1
    WHERE t.demo_mode AND t.status = 'TRIAL' AND t.is_active AND (t.trial_started_at IS NOT NULL OR t.trial_ends_at IS NOT NULL)
      AND EXISTS (SELECT 1 FROM common_company.common_subscription s WHERE s.tenant_id = t.id
        AND s.evaluation_selection_source = 'REPAIRED_PRESERVED')
      AND NOT EXISTS (SELECT 1 FROM common_company.common_subscription paid WHERE paid.tenant_id = t.id AND paid.status = 'ACTIVE');
  -- Whatever is still expired without a provable cause is left exactly as found and recorded for the
  -- operator. Cancelled and suspended rows are an owner/operator decision and never appear here.
  INSERT INTO common_company.evaluation_reconciliation(lead_id, observed_tenant_id, reason, details)
  SELECT l.id, t.id, 'EXPIRED_PACKAGE_UNPROVEN',
    jsonb_build_object('packages', jsonb_agg(jsonb_build_object('osCode', s.os_code,
      'expiryDate', s.expiry_date, 'trialEndsAt', s.trial_ends_at) ORDER BY s.os_code))
  FROM common_tenant.common_tenant t
  JOIN common_user.onboarding_journey j ON j.tenant_id = t.id AND j.kind = 'COMPANY' AND j.signup_intent = 'PLAYGROUND'
  JOIN common_company.common_lead l ON l.signup_id = j.signup_id AND l.signup_tenant_id = t.id
  JOIN common_company.common_subscription s ON s.tenant_id = t.id
  WHERE t.demo_mode AND t.status = 'TRIAL' AND t.is_active
    AND s.status = 'EXPIRED' AND s.is_active AND s.deleted_at IS NULL AND s.evaluation_selection_source IS NULL
  GROUP BY l.id, t.id
  ON CONFLICT (lead_id, reason) DO UPDATE SET details = EXCLUDED.details, observed_at = now()
    WHERE common_company.evaluation_reconciliation.details IS DISTINCT FROM EXCLUDED.details;
  RETURN repaired;
END $$;
SELECT common_company.repair_playground_evaluation_packages();
REVOKE ALL ON FUNCTION common_company.repair_playground_evaluation_packages() FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    REVOKE ALL ON FUNCTION common_company.repair_playground_evaluation_packages() FROM fabric_app;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    REVOKE ALL ON FUNCTION common_company.repair_playground_evaluation_packages() FROM fabric_system;
  END IF;
END $$;
