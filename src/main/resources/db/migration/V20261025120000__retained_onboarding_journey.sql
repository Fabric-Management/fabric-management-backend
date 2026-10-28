-- REG-ONB-04: operational tenant progress and independent, credential-free prospect evidence.
ALTER TABLE common_company.common_lead
  ADD COLUMN signup_id uuid NOT NULL DEFAULT gen_random_uuid(),
  ADD COLUMN signup_tenant_id uuid,
  ADD COLUMN signup_user_id uuid,
  ADD COLUMN signup_provenance varchar(40) NOT NULL DEFAULT 'SIGNUP',
  ADD COLUMN terms_accepted_at timestamptz,
  ADD COLUMN signup_snapshot jsonb,
  ADD COLUMN accepted_snapshot jsonb,
  ADD COLUMN accepted_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE common_company.common_lead ALTER COLUMN tax_id DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lead_signup ON common_company.common_lead(signup_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lead_registration_link ON common_company.common_lead(signup_tenant_id, signup_user_id)
  WHERE signup_tenant_id IS NOT NULL AND signup_user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS common_company.lead_profile_snapshot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), lead_id uuid NOT NULL,
  revision bigint NOT NULL, source varchar(40) NOT NULL, step_id varchar(40), operation_id uuid,
  actor_user_id uuid, accepted_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
  evidence jsonb NOT NULL,
  CONSTRAINT fk_lead_snapshot_parent FOREIGN KEY(lead_id) REFERENCES common_company.common_lead(id) ON DELETE CASCADE,
  CONSTRAINT uk_lead_snapshot_revision UNIQUE(lead_id, revision),
  CONSTRAINT ck_lead_snapshot_revision CHECK(revision >= 0)
);
-- Deliberately no tenant_id, tenant FK or RLS: only the retained Lead owns its history.
CREATE TABLE IF NOT EXISTS common_company.onboarding_link_issue (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_id uuid UNIQUE REFERENCES common_company.common_lead(id) ON DELETE CASCADE,
  observed_tenant_id uuid UNIQUE,
  reason varchar(80) NOT NULL, observed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS common_user.onboarding_journey (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL,
  uid varchar(100) UNIQUE, owner_user_id uuid NOT NULL, signup_id uuid,
  kind varchar(20) NOT NULL CHECK(kind IN ('COMPANY','PERSONAL')), signup_intent varchar(40),
  accepted_steps jsonb NOT NULL DEFAULT '{}', review_operation_id uuid, finalized_review_operation_id uuid, completed_at timestamptz,
  last_accepted_at timestamptz, personal_mobile varchar(20), legacy_completed boolean NOT NULL DEFAULT false,
  command_version bigint NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
  FOREIGN KEY(tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE CASCADE,
  FOREIGN KEY(owner_user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
  UNIQUE(tenant_id, owner_user_id), UNIQUE(signup_id),
  CHECK((kind = 'COMPANY' AND signup_id IS NOT NULL) OR (kind = 'PERSONAL' AND signup_id IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_onboarding_company_journey ON common_user.onboarding_journey(tenant_id) WHERE kind = 'COMPANY';
CREATE TABLE IF NOT EXISTS common_user.onboarding_operation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, uid varchar(100) UNIQUE,
  journey_id uuid NOT NULL REFERENCES common_user.onboarding_journey(id) ON DELETE CASCADE,
  operation_id uuid NOT NULL, actor_user_id uuid NOT NULL, command_hash varchar(64) NOT NULL, result jsonb NOT NULL,
  is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
  FOREIGN KEY(tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE CASCADE,
  UNIQUE(journey_id, operation_id)
);
CREATE INDEX IF NOT EXISTS idx_onboarding_operation_tenant ON common_user.onboarding_operation(tenant_id, journey_id);
ALTER TABLE common_user.onboarding_journey ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.onboarding_journey FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON common_user.onboarding_journey
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
ALTER TABLE common_user.onboarding_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.onboarding_operation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON common_user.onboarding_operation
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Operator-only, idempotent recovery. Registration metadata + tenant + real root owner identify
-- a signup. Never match by email alone, read token values, infer consent, paid conversion or steps.
CREATE FUNCTION common_company.backfill_onboarding_evidence() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  DROP TABLE IF EXISTS pg_temp.onboarding_legacy_candidates;
  CREATE TEMP TABLE onboarding_legacy_candidates ON COMMIT DROP AS
    SELECT DISTINCT u.tenant_id, u.id AS user_id, u.organization_id, u.first_name, u.last_name,
      rt.contact_value AS work_email, o.name AS company_name, o.tax_id, o.organization_type,
      u.onboarding_completed_at, o.legal_name, o.registration_number, o.industry, o.website, o.description
    FROM common_auth.common_registration_token rt
    JOIN common_user.common_user u ON u.id = rt.user_id AND u.tenant_id = rt.tenant_id
    JOIN common_company.common_organization o ON o.id = rt.organization_id AND o.id = u.organization_id
      AND o.tenant_id = u.tenant_id AND o.parent_organization_id IS NULL AND o.organization_type <> 'EXTERNAL_PARTNER'
    WHERE rt.token_type IN ('SELF_SERVICE','SALES_LED') AND u.demo_seed = false
      AND u.user_type = 'INTERNAL';

  -- Missing historical captures are recoverable observations, not invented original signup facts.
  INSERT INTO common_company.common_lead(uid, company_name, tax_id, organization_type, first_name,
    last_name, work_email, selected_os, signup_intent, trial_tenant_id, signup_tenant_id, signup_user_id,
    signup_provenance)
  SELECT 'LEAD-' || replace(gen_random_uuid()::text, '-', ''), c.company_name, c.tax_id,
    c.organization_type, c.first_name, c.last_name, c.work_email, '[]', 'LEGACY_UNKNOWN',
    c.tenant_id, c.tenant_id, c.user_id, 'LEGACY_RECORDS'
  FROM onboarding_legacy_candidates c
  WHERE (SELECT count(*) FROM onboarding_legacy_candidates x WHERE x.tenant_id = c.tenant_id) = 1
    AND NOT EXISTS(SELECT 1 FROM common_company.common_lead l
      WHERE l.trial_tenant_id = c.tenant_id OR l.signup_tenant_id = c.tenant_id)
    -- An operational signup link without a Lead may reflect separately authorised erasure.
    -- Do not reconstruct its prospect evidence from residual account records.
    AND NOT EXISTS(SELECT 1 FROM common_user.onboarding_journey j
      WHERE j.tenant_id = c.tenant_id AND j.kind = 'COMPANY');

  UPDATE common_company.common_lead l SET signup_tenant_id = l.trial_tenant_id
    WHERE signup_tenant_id IS NULL AND trial_tenant_id IS NOT NULL;
  UPDATE common_company.common_lead l SET signup_user_id = c.user_id
  FROM onboarding_legacy_candidates c
  WHERE l.signup_tenant_id = c.tenant_id AND l.signup_user_id IS NULL
    AND lower(l.work_email) = lower(c.work_email)
    AND (SELECT count(*) FROM common_company.common_lead x WHERE x.signup_tenant_id = c.tenant_id) = 1
    AND (SELECT count(*) FROM onboarding_legacy_candidates x WHERE x.tenant_id = c.tenant_id) = 1;

  UPDATE common_company.common_lead l SET signup_snapshot = jsonb_build_object(
      'organisation', jsonb_build_object('organizationName', l.company_name, 'taxId', l.tax_id,
        'organizationType', l.organization_type),
      'registrant', jsonb_build_object('firstName', l.first_name, 'lastName', l.last_name, 'workEmail', l.work_email),
      'selectedOS', CASE WHEN l.signup_provenance = 'LEGACY_RECORDS' THEN NULL ELSE l.selected_os END,
      'knownFields', jsonb_build_array('organizationName','organizationType','firstName','lastName','workEmail')
        || CASE WHEN l.tax_id IS NULL THEN '[]'::jsonb ELSE '["taxId"]'::jsonb END
        || CASE WHEN l.signup_provenance = 'LEGACY_RECORDS' THEN '[]'::jsonb ELSE '["selectedOS"]'::jsonb END)
    WHERE l.signup_snapshot IS NULL;

  -- Verifiable live profile observations are enriched. No accepted step is inferred.
  -- The following card pass retains only unambiguous root/registrant-owned records.
  UPDATE common_company.common_lead l SET accepted_snapshot = l.signup_snapshot || jsonb_build_object(
      'organisation', jsonb_build_object('organizationName', c.company_name, 'taxId', COALESCE(c.tax_id, l.tax_id),
        'organizationType', c.organization_type, 'legalName', c.legal_name,
        'registrationNumber', c.registration_number, 'industry', c.industry,
        'website', c.website, 'description', c.description),
      'knownFields', l.signup_snapshot->'knownFields'
        || CASE WHEN c.legal_name IS NULL THEN '[]'::jsonb ELSE '["legalName"]'::jsonb END
        || CASE WHEN c.registration_number IS NULL THEN '[]'::jsonb ELSE '["registrationNumber"]'::jsonb END
        || CASE WHEN c.industry IS NULL THEN '[]'::jsonb ELSE '["industry"]'::jsonb END
        || CASE WHEN c.website IS NULL THEN '[]'::jsonb ELSE '["website"]'::jsonb END
        || CASE WHEN c.description IS NULL THEN '[]'::jsonb ELSE '["description"]'::jsonb END)
    FROM onboarding_legacy_candidates c
    WHERE l.signup_user_id = c.user_id AND l.signup_tenant_id = c.tenant_id AND l.accepted_snapshot IS NULL;
  -- Retain only unambiguous cards owned by the proven real root/registrant. Partner/demo users
  -- are excluded by the registration linkage; personal/shared company cards are not borrowed.
  WITH live AS (
    SELECT l.id, a.payload AS address, co.payload AS contacts, mobile.value AS mobile
    FROM common_company.common_lead l
    JOIN onboarding_legacy_candidates c ON c.user_id = l.signup_user_id AND c.tenant_id = l.signup_tenant_id
    LEFT JOIN LATERAL (
      SELECT jsonb_agg(card.payload)->0 AS payload
      FROM (
        SELECT jsonb_strip_nulls(jsonb_build_object('addressLine1', d.street_address,
          'addressLine2', d.address_line2, 'city', d.city, 'state', d.state, 'district', d.district,
          'postalCode', d.postal_code, 'country', d.country, 'addressLabel', d.label)) AS payload
        FROM common_company.common_organization_address oa
        JOIN common_communication.common_address d ON d.id = oa.address_id AND d.tenant_id = oa.tenant_id
        WHERE oa.organization_id = c.organization_id AND oa.tenant_id = c.tenant_id AND oa.is_active AND d.is_active
          AND (oa.is_headquarters OR (oa.is_primary AND d.address_type = 'HEADQUARTERS'))
          AND NOT EXISTS(SELECT 1 FROM common_company.common_organization_address other
            WHERE other.address_id = d.id AND other.organization_id <> c.organization_id AND other.is_active)
          AND NOT EXISTS(SELECT 1 FROM common_user.common_user_address personal
            WHERE personal.address_id = d.id AND personal.is_active)
      ) card HAVING count(*) = 1
    ) a ON true
    LEFT JOIN LATERAL (
      SELECT NULLIF(jsonb_strip_nulls(jsonb_build_object(
        'companyEmail', (SELECT max(d.contact_value) FROM common_company.common_organization_contact oc
          JOIN common_communication.common_contact d ON d.id = oc.contact_id AND d.tenant_id = oc.tenant_id
          WHERE oc.organization_id = c.organization_id AND oc.tenant_id = c.tenant_id
            AND oc.is_active AND d.is_active AND NOT d.is_personal AND d.contact_type = 'EMAIL' HAVING count(*) = 1),
        'companyPhone', (SELECT max(d.contact_value) FROM common_company.common_organization_contact oc
          JOIN common_communication.common_contact d ON d.id = oc.contact_id AND d.tenant_id = oc.tenant_id
          WHERE oc.organization_id = c.organization_id AND oc.tenant_id = c.tenant_id
            AND oc.is_active AND d.is_active AND NOT d.is_personal AND d.contact_type IN ('MOBILE','LANDLINE') HAVING count(*) = 1)
      )), '{}'::jsonb) AS payload
    ) co ON true
    LEFT JOIN LATERAL (
      SELECT max(d.contact_value) AS value FROM common_user.common_user_contact uc
      JOIN common_communication.common_contact d ON d.id = uc.contact_id AND d.tenant_id = uc.tenant_id
      WHERE uc.user_id = c.user_id AND uc.tenant_id = c.tenant_id AND uc.is_active AND d.is_active
        AND d.contact_type = 'MOBILE' AND d.is_personal HAVING count(*) = 1
    ) mobile ON true
    WHERE l.accepted_revision = 0 AND NOT EXISTS(
      SELECT 1 FROM common_company.lead_profile_snapshot history WHERE history.lead_id = l.id)
  )
  UPDATE common_company.common_lead l SET accepted_snapshot = l.accepted_snapshot || jsonb_build_object(
    'headquarters', live.address, 'contacts', live.contacts,
    'registrant', (l.accepted_snapshot->'registrant') || CASE WHEN live.mobile IS NULL THEN '{}'::jsonb
      ELSE jsonb_build_object('mobilePhone', live.mobile) END,
    'knownFields', (l.accepted_snapshot->'knownFields')
      || COALESCE((SELECT jsonb_agg(k) FROM jsonb_object_keys(COALESCE(live.address, '{}'::jsonb)) AS keys(k)), '[]'::jsonb)
      || COALESCE((SELECT jsonb_agg(k) FROM jsonb_object_keys(COALESCE(live.contacts, '{}'::jsonb)) AS keys(k)), '[]'::jsonb)
      || CASE WHEN live.mobile IS NULL THEN '[]'::jsonb ELSE '["mobilePhone"]'::jsonb END)
  FROM live WHERE live.id = l.id;
  UPDATE common_company.common_lead SET accepted_snapshot = signup_snapshot WHERE accepted_snapshot IS NULL;

  INSERT INTO common_company.lead_profile_snapshot(lead_id, revision, source, actor_user_id, evidence, accepted_at)
    SELECT l.id, 0, 'LEGACY_BACKFILL', l.signup_user_id, l.accepted_snapshot, now()
    FROM common_company.common_lead l
    WHERE NOT EXISTS(SELECT 1 FROM common_company.lead_profile_snapshot s WHERE s.lead_id = l.id);
  INSERT INTO common_user.onboarding_journey(tenant_id, uid, owner_user_id, signup_id, kind,
      signup_intent, completed_at, legacy_completed)
    SELECT l.signup_tenant_id, 'ONB-' || l.signup_id::text, l.signup_user_id, l.signup_id, 'COMPANY',
      l.signup_intent, c.onboarding_completed_at, c.onboarding_completed_at IS NOT NULL
    FROM common_company.common_lead l JOIN onboarding_legacy_candidates c
      ON c.user_id = l.signup_user_id AND c.tenant_id = l.signup_tenant_id
    WHERE NOT EXISTS(SELECT 1 FROM common_user.onboarding_journey j WHERE j.tenant_id = l.signup_tenant_id AND j.kind = 'COMPANY')
    ON CONFLICT DO NOTHING;
  INSERT INTO common_company.onboarding_link_issue(lead_id, reason)
    SELECT l.id, CASE WHEN l.signup_tenant_id IS NULL THEN 'TENANT_LINK_UNKNOWN_OR_REMOVED'
      WHEN (SELECT count(*) FROM common_company.common_lead x WHERE x.signup_tenant_id = l.signup_tenant_id) > 1
        THEN 'MULTIPLE_LEADS_FOR_TENANT' ELSE 'REGISTRANT_LINK_UNRESOLVED' END
    FROM common_company.common_lead l WHERE l.signup_user_id IS NULL
    ON CONFLICT(lead_id) DO UPDATE SET reason = EXCLUDED.reason, observed_at = now();
  DELETE FROM common_company.onboarding_link_issue i USING common_company.common_lead l
    WHERE i.lead_id = l.id AND l.signup_user_id IS NOT NULL;
  -- Identity membership without registration provenance is not proof of company ownership.
  -- Record these unresolved real companies for operator reconciliation, without inventing Leads.
  INSERT INTO common_company.onboarding_link_issue(observed_tenant_id, reason)
    SELECT DISTINCT o.tenant_id, 'SIGNUP_PROVENANCE_UNRESOLVED'
    FROM common_company.common_organization o
    JOIN common_user.common_user u ON u.organization_id = o.id AND u.tenant_id = o.tenant_id
    JOIN common_auth.membership m ON m.user_id = u.id AND m.tenant_id = u.tenant_id
    WHERE o.parent_organization_id IS NULL AND o.organization_type <> 'EXTERNAL_PARTNER'
      AND u.demo_seed = false AND u.user_type = 'INTERNAL'
      AND o.tenant_id NOT IN ('00000000-0000-0000-0000-000000000000'::uuid,
        '00000000-0000-0000-ffff-000000000001'::uuid)
      AND NOT EXISTS(SELECT 1 FROM common_company.common_lead l WHERE l.signup_tenant_id = o.tenant_id)
    ON CONFLICT(observed_tenant_id) DO UPDATE SET reason = EXCLUDED.reason, observed_at = now();
  DELETE FROM common_company.onboarding_link_issue i USING common_company.common_lead l
    WHERE i.observed_tenant_id = l.signup_tenant_id AND l.signup_user_id IS NOT NULL;
END $$;
SELECT common_company.backfill_onboarding_evidence();
REVOKE ALL ON FUNCTION common_company.backfill_onboarding_evidence() FROM PUBLIC;

CREATE FUNCTION common_company.guard_signup_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF current_user IN ('fabric_app','fabric_system') AND
    (NEW.signup_id, NEW.signup_tenant_id, NEW.signup_user_id, NEW.signup_snapshot,
     NEW.company_name, NEW.tax_id, NEW.organization_type, NEW.first_name, NEW.last_name,
     NEW.work_email, NEW.selected_os, NEW.signup_intent, NEW.terms_accepted_at, NEW.signup_provenance,
     NEW.uid, NEW.created_at, NEW.created_by)
    IS DISTINCT FROM
    (OLD.signup_id, OLD.signup_tenant_id, OLD.signup_user_id, OLD.signup_snapshot,
     OLD.company_name, OLD.tax_id, OLD.organization_type, OLD.first_name, OLD.last_name,
     OLD.work_email, OLD.selected_os, OLD.signup_intent, OLD.terms_accepted_at, OLD.signup_provenance,
     OLD.uid, OLD.created_at, OLD.created_by)
  THEN RAISE EXCEPTION 'Original signup evidence is immutable' USING ERRCODE = '42501'; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER immutable_signup_evidence BEFORE UPDATE ON common_company.common_lead
  FOR EACH ROW EXECUTE FUNCTION common_company.guard_signup_evidence();

DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    REVOKE ALL ON common_company.onboarding_link_issue FROM fabric_app;
    REVOKE ALL ON FUNCTION common_company.backfill_onboarding_evidence() FROM fabric_app;
    REVOKE ALL ON common_company.lead_profile_snapshot, common_user.onboarding_operation FROM fabric_app;
    REVOKE DELETE ON common_company.common_lead, common_user.onboarding_journey FROM fabric_app;
    GRANT SELECT, INSERT ON common_company.lead_profile_snapshot, common_user.onboarding_operation TO fabric_app;
    GRANT SELECT, INSERT, UPDATE ON common_company.common_lead, common_user.onboarding_journey TO fabric_app;
  END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    REVOKE ALL ON common_company.onboarding_link_issue FROM fabric_system;
    REVOKE ALL ON FUNCTION common_company.backfill_onboarding_evidence() FROM fabric_system;
    REVOKE UPDATE ON common_company.lead_profile_snapshot, common_user.onboarding_operation FROM fabric_system;
    GRANT SELECT, INSERT, DELETE ON common_company.lead_profile_snapshot, common_user.onboarding_operation TO fabric_system;
    GRANT SELECT, INSERT, UPDATE, DELETE ON common_company.common_lead, common_user.onboarding_journey TO fabric_system;
  END IF;
END $$;
REVOKE ALL ON common_company.onboarding_link_issue, common_company.lead_profile_snapshot,
  common_user.onboarding_journey, common_user.onboarding_operation FROM PUBLIC;
