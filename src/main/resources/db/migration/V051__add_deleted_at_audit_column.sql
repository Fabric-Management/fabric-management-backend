-- ============================================================================
-- V1_1_4: Add deleted_at audit timestamp to all entity tables
-- ============================================================================
-- Enhances the existing soft-delete (is_active) mechanism with a timestamp
-- that records WHEN an entity was logically deleted.
--
-- - Non-breaking: nullable column, NULL default, no data migration needed
-- - Provides audit trail for soft-deleted records
-- - Uses ADD COLUMN IF NOT EXISTS for idempotent execution
-- ============================================================================

-- ── common_tenant ──────────────────────────────────────────────────────────
ALTER TABLE common_tenant.common_tenant
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_user ────────────────────────────────────────────────────────────
ALTER TABLE common_user.common_user
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_user.common_role
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_user.profile_update_request
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Junction tables (BaseJunctionEntity)
ALTER TABLE common_user.common_user_department
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_user.common_user_contact
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_user.common_user_address
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_user.common_user_work_location
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_communication ───────────────────────────────────────────────────
ALTER TABLE common_communication.common_contact
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_communication.common_address
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_communication.common_verification_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_communication.common_routing_config
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_communication.communication_email_outbox
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_company (organization) ──────────────────────────────────────────
ALTER TABLE common_company.common_organization
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_department
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_os_definition
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_subscription
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_feature_catalog
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_subscription_quota
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_trading_partner
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.trading_partner_registry
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Junction tables (BaseJunctionEntity)
ALTER TABLE common_company.common_organization_contact
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_company.common_organization_address
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_auth ────────────────────────────────────────────────────────────
ALTER TABLE common_auth.common_auth_user
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_auth.common_refresh_token
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_auth.common_verification_code
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_auth.common_registration_token
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE common_auth.common_trusted_device
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_policy ──────────────────────────────────────────────────────────
ALTER TABLE common_policy.common_policy
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_audit ───────────────────────────────────────────────────────────
ALTER TABLE common_audit.common_audit_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── common_ai ──────────────────────────────────────────────────────────────
ALTER TABLE common_ai.common_ai_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── finance ────────────────────────────────────────────────────────────────
ALTER TABLE finance.finance_invoice
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── human (HR) ─────────────────────────────────────────────────────────────
ALTER TABLE human.human_employee
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_hr_policy_pack
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_hr_rule_version
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_hr_policy_binding
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_hr_rule_audit_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_leave_type
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_leave_balance
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_leave_accrual_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_holiday_calendar
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_pay_period
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_pay_run
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_pay_run_entry
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_pay_run_payout
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_pay_run_audit_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE human.human_hr_country_pack_mapping
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── logistics ──────────────────────────────────────────────────────────────
ALTER TABLE logistics.logistics_sales_order
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE logistics.logistics_shipment
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── production ─────────────────────────────────────────────────────────────
ALTER TABLE production.prod_material
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber_category
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber_attribute
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber_certification
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber_iso_code
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber_attribute_link
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_fiber_certification_link
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_yarn_category
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_yarn_attribute
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.prod_yarn_certification
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.production_execution_fiber_batch
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE production.production_quality_fiber_test_result
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ── Partial index for efficient soft-delete queries on key tables ──────────
CREATE INDEX IF NOT EXISTS idx_address_active
    ON common_communication.common_address (tenant_id)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_org_address_active
    ON common_company.common_organization_address (organization_id)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_user_work_location_active
    ON common_user.common_user_work_location (org_address_id)
    WHERE is_active = TRUE;
