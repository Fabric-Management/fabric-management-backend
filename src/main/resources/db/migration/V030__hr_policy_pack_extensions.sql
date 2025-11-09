-- ============================================================================
-- V030: HR Policy Pack Extensions
-- ----------------------------------------------------------------------------
-- Adds supporting tables for rule versions, policy bindings, and audit logs.
-- Enables nullable effective_from for draft policy packs and enforces payload
-- non-null constraint. Also introduces audit trail for publish/retire actions.
-- Last Updated: 2025-11-09
-- ============================================================================

ALTER TABLE human.human_hr_policy_pack
    ALTER COLUMN effective_from DROP NOT NULL,
    ALTER COLUMN payload SET NOT NULL;

-- ============================================================================
-- TABLE: human_hr_rule_version
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_hr_rule_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    policy_pack_id UUID NOT NULL,
    rule_type VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hr_rule_version_pack FOREIGN KEY (policy_pack_id)
        REFERENCES human.human_hr_policy_pack(id) ON DELETE CASCADE
);

CREATE INDEX idx_hr_rule_version_pack ON human.human_hr_rule_version(policy_pack_id);
CREATE INDEX idx_hr_rule_version_tenant ON human.human_hr_rule_version(tenant_id);
CREATE INDEX idx_hr_rule_version_type ON human.human_hr_rule_version(rule_type);

-- ============================================================================
-- TABLE: human_hr_policy_binding
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_hr_policy_binding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    policy_pack_id UUID NOT NULL,
    policy_interface VARCHAR(150) NOT NULL,
    strategy_bean VARCHAR(150) NOT NULL,
    config_reference JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hr_policy_binding_pack FOREIGN KEY (policy_pack_id)
        REFERENCES human.human_hr_policy_pack(id) ON DELETE CASCADE
);

CREATE INDEX idx_hr_policy_binding_pack ON human.human_hr_policy_binding(policy_pack_id);
CREATE INDEX idx_hr_policy_binding_interface ON human.human_hr_policy_binding(policy_interface);

-- ============================================================================
-- TABLE: human_hr_rule_audit_log
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_hr_rule_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    policy_pack_id UUID NOT NULL,
    pack_code VARCHAR(100) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    pack_version INTEGER NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_id UUID,
    payload_checksum VARCHAR(128),
    diff_snapshot JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_hr_rule_audit_pack ON human.human_hr_rule_audit_log(policy_pack_id);
CREATE INDEX idx_hr_rule_audit_action ON human.human_hr_rule_audit_log(action);
CREATE INDEX idx_hr_rule_audit_tenant ON human.human_hr_rule_audit_log(tenant_id);

COMMENT ON TABLE human.human_hr_rule_version IS 'Stores per-rule configuration payloads tied to HR policy packs.';
COMMENT ON TABLE human.human_hr_policy_binding IS 'Maps policy interfaces to strategy bean implementations for a policy pack.';
COMMENT ON TABLE human.human_hr_rule_audit_log IS 'Audit log for HR policy pack lifecycle events (publish, retire).';

