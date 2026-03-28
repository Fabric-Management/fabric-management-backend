-- ============================================
-- MODULE: HR (Human Resources)
-- Kaynak: V019, V021, V023, V024, V029–V033
-- Not: V022 (common_position, common_user_position) atlandı — V050 ile kaldırıldı.
-- ============================================

-- ============================================================================
-- SCHEMA & SEQUENCES
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS human;
CREATE SEQUENCE IF NOT EXISTS human.seq_employee START 1000;

COMMENT ON SCHEMA human IS 'Human Resources module - Employee, leave, payroll, HR policy packs';

-- ============================================================================
-- 1. profile_update_request (common_user — FK common_user)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.profile_update_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    profile_category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    requested_changes JSONB,
    reason TEXT,
    reviewed_by UUID,
    review_comment TEXT,
    reviewed_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_profile_req_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_req_reviewer FOREIGN KEY (reviewed_by) REFERENCES common_user.common_user(id) ON DELETE SET NULL
);

CREATE INDEX idx_profile_req_user ON common_user.profile_update_request(tenant_id, user_id);
CREATE INDEX idx_profile_req_status ON common_user.profile_update_request(tenant_id, status);
CREATE INDEX idx_profile_req_pending ON common_user.profile_update_request(tenant_id, status) WHERE status = 'PENDING';

-- ============================================================================
-- 2. human_employee
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_employee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    title VARCHAR(20),
    gender VARCHAR(20),
    birth_date DATE,
    nationality VARCHAR(2),
    employee_number VARCHAR(50),
    hire_date DATE,
    termination_date DATE,
    emergency_contact_name VARCHAR(100),
    emergency_contact_phone VARCHAR(50),
    emergency_contact_relationship VARCHAR(50),
    hr_compliance_status VARCHAR(30),
    missing_fields VARCHAR(500),
    last_compliance_check_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_employee_user ON human.human_employee(user_id);
CREATE INDEX idx_employee_tenant ON human.human_employee(tenant_id);
CREATE UNIQUE INDEX idx_employee_employee_number ON human.human_employee(tenant_id, employee_number) WHERE employee_number IS NOT NULL;
CREATE INDEX idx_employee_compliance_status ON human.human_employee(tenant_id, hr_compliance_status) WHERE hr_compliance_status IS NOT NULL;

-- ============================================================================
-- 3. human_employee_number_sequence
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_employee_number_sequence (
    tenant_id UUID PRIMARY KEY,
    next_sequence INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 4. human_hr_policy_pack (V029 + V030 + V033: effective_from nullable, payload NOT NULL, parent/hierarchy)
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_hr_policy_pack (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pack_code VARCHAR(100) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    pack_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    payload JSONB NOT NULL,
    checksum VARCHAR(128),
    parent_pack_id UUID,
    parent_pack_code VARCHAR(100),
    region_code VARCHAR(50),
    inheritance_mode VARCHAR(20) NOT NULL DEFAULT 'FULL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hr_policy_pack_parent FOREIGN KEY (parent_pack_id) REFERENCES human.human_hr_policy_pack(id)
);

CREATE UNIQUE INDEX uq_hr_policy_pack_code ON human.human_hr_policy_pack(tenant_id, pack_code, pack_version);
CREATE INDEX idx_hr_policy_pack_tenant_country ON human.human_hr_policy_pack(tenant_id, country_code, status);
CREATE INDEX idx_hr_policy_pack_parent ON human.human_hr_policy_pack(parent_pack_id);
CREATE INDEX idx_hr_policy_pack_region ON human.human_hr_policy_pack(tenant_id, region_code);

-- ============================================================================
-- 5. human_hr_rule_version, human_hr_policy_binding, human_hr_rule_audit_log
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
    CONSTRAINT fk_hr_rule_version_pack FOREIGN KEY (policy_pack_id) REFERENCES human.human_hr_policy_pack(id) ON DELETE CASCADE
);

CREATE INDEX idx_hr_rule_version_pack ON human.human_hr_rule_version(policy_pack_id);

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
    CONSTRAINT fk_hr_policy_binding_pack FOREIGN KEY (policy_pack_id) REFERENCES human.human_hr_policy_pack(id) ON DELETE CASCADE
);

CREATE INDEX idx_hr_policy_binding_pack ON human.human_hr_policy_binding(policy_pack_id);

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

-- ============================================================================
-- 6. human_hr_country_pack_mapping
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_hr_country_pack_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    pack_code VARCHAR(100) NOT NULL,
    pack_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_country_pack UNIQUE (tenant_id, country_code),
    CONSTRAINT fk_country_pack_policy FOREIGN KEY (pack_id) REFERENCES human.human_hr_policy_pack(id)
);

CREATE INDEX idx_country_pack_code ON human.human_hr_country_pack_mapping(tenant_id, pack_code);

-- ============================================================================
-- 7. human_leave_type, human_leave_balance, human_leave_accrual_log, human_holiday_calendar
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_leave_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    country_code VARCHAR(8),
    statutory BOOLEAN NOT NULL DEFAULT FALSE,
    accrual_strategy VARCHAR(150) NOT NULL,
    default_accrual_rate NUMERIC(10,4) DEFAULT 0,
    max_carry_over NUMERIC(10,4) DEFAULT 0,
    attributes JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_leave_type_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_leave_type_country ON human.human_leave_type(tenant_id, country_code);

CREATE TABLE IF NOT EXISTS human.human_leave_balance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    employee_id UUID NOT NULL,
    leave_type_id UUID NOT NULL,
    balance_days NUMERIC(12,4) NOT NULL DEFAULT 0,
    carry_over_days NUMERIC(12,4) NOT NULL DEFAULT 0,
    pending_days NUMERIC(12,4) NOT NULL DEFAULT 0,
    last_accrual_at TIMESTAMP,
    policy_pack_code VARCHAR(100),
    policy_pack_version INTEGER,
    country_code VARCHAR(8),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_leave_balance UNIQUE (tenant_id, employee_id, leave_type_id),
    CONSTRAINT fk_leave_balance_type FOREIGN KEY (leave_type_id) REFERENCES human.human_leave_type(id) ON DELETE CASCADE
);

CREATE INDEX idx_leave_balance_employee ON human.human_leave_balance(tenant_id, employee_id);

CREATE TABLE IF NOT EXISTS human.human_leave_accrual_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    employee_id UUID NOT NULL,
    leave_type_id UUID NOT NULL,
    accrual_amount NUMERIC(12,4) NOT NULL,
    balance_after NUMERIC(12,4) NOT NULL,
    policy_pack_code VARCHAR(100),
    policy_pack_version INTEGER,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    context JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_leave_accrual_type FOREIGN KEY (leave_type_id) REFERENCES human.human_leave_type(id) ON DELETE CASCADE
);

CREATE INDEX idx_leave_accrual_employee ON human.human_leave_accrual_log(tenant_id, employee_id);

CREATE TABLE IF NOT EXISTS human.human_holiday_calendar (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    calendar_year INTEGER NOT NULL,
    entries JSONB NOT NULL,
    version_tag VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_holiday_calendar UNIQUE (tenant_id, country_code, calendar_year)
);

CREATE INDEX idx_holiday_calendar_country ON human.human_holiday_calendar(tenant_id, country_code);

-- ============================================================================
-- 8. human_pay_period, human_pay_run, human_pay_run_entry, human_pay_run_payout, human_pay_run_audit_log
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_pay_period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    period_code VARCHAR(50) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    frequency VARCHAR(30),
    locked_at TIMESTAMP,
    locked_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_pay_period UNIQUE (tenant_id, period_code)
);

CREATE TABLE IF NOT EXISTS human.human_pay_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pay_period_id UUID NOT NULL,
    run_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    policy_pack_code VARCHAR(100),
    policy_pack_version INTEGER,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    initiated_by UUID,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_period FOREIGN KEY (pay_period_id) REFERENCES human.human_pay_period(id) ON DELETE CASCADE,
    CONSTRAINT uq_pay_run_period UNIQUE (pay_period_id, run_number)
);

CREATE TABLE IF NOT EXISTS human.human_pay_run_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pay_run_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    taxable BOOLEAN NOT NULL DEFAULT TRUE,
    policy_reference JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_entry_run FOREIGN KEY (pay_run_id) REFERENCES human.human_pay_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_pay_run_entry_employee ON human.human_pay_run_entry(pay_run_id, employee_id);

CREATE TABLE IF NOT EXISTS human.human_pay_run_payout (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pay_run_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    net_amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_channel VARCHAR(50),
    payout_reference VARCHAR(100),
    processed_at TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_payout_run FOREIGN KEY (pay_run_id) REFERENCES human.human_pay_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_pay_run_payout_employee ON human.human_pay_run_payout(pay_run_id, employee_id);

CREATE TABLE IF NOT EXISTS human.human_pay_run_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pay_run_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_id UUID,
    message TEXT,
    payload JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_audit FOREIGN KEY (pay_run_id) REFERENCES human.human_pay_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_pay_run_audit_action ON human.human_pay_run_audit_log(pay_run_id, action);

-- [HR] module migration tamamlandı.
