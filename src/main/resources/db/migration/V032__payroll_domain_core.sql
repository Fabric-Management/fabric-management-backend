-- ============================================================================
-- V032: Payroll Domain Core
-- ----------------------------------------------------------------------------
-- Creates foundational tables for pay periods, pay runs, run entries, payouts,
-- and audit trail. Enables localization via policy pack references.
-- Last Updated: 2025-11-09
-- ============================================================================

-- ============================================================================
-- TABLE: human_pay_period
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_pay_period UNIQUE (tenant_id, period_code)
);

CREATE INDEX idx_pay_period_country ON human.human_pay_period(tenant_id, country_code);
CREATE INDEX idx_pay_period_status ON human.human_pay_period(tenant_id, status);

COMMENT ON TABLE human.human_pay_period IS 'Defines payroll periods per tenant/country.';

-- ============================================================================
-- TABLE: human_pay_run
-- ============================================================================
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_period FOREIGN KEY (pay_period_id)
        REFERENCES human.human_pay_period(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_pay_run_period ON human.human_pay_run(pay_period_id, run_number);
CREATE INDEX idx_pay_run_status ON human.human_pay_run(tenant_id, status);

COMMENT ON TABLE human.human_pay_run IS 'Instances of payroll processing tied to a pay period.';

-- ============================================================================
-- TABLE: human_pay_run_entry
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_pay_run_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pay_run_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    entry_type VARCHAR(20) NOT NULL, -- EARNING / DEDUCTION
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_entry_run FOREIGN KEY (pay_run_id)
        REFERENCES human.human_pay_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_pay_run_entry_employee ON human.human_pay_run_entry(pay_run_id, employee_id);
CREATE INDEX idx_pay_run_entry_code ON human.human_pay_run_entry(tenant_id, code);

COMMENT ON TABLE human.human_pay_run_entry IS 'Detailed earning/deduction lines for payroll runs.';

-- ============================================================================
-- TABLE: human_pay_run_payout
-- ============================================================================
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_payout_run FOREIGN KEY (pay_run_id)
        REFERENCES human.human_pay_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_pay_run_payout_employee ON human.human_pay_run_payout(pay_run_id, employee_id);
CREATE INDEX idx_pay_run_payout_status ON human.human_pay_run_payout(tenant_id, status);

COMMENT ON TABLE human.human_pay_run_payout IS 'Tracks net payouts for employees per payroll run.';

-- ============================================================================
-- TABLE: human_pay_run_audit_log
-- ============================================================================
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_run_audit FOREIGN KEY (pay_run_id)
        REFERENCES human.human_pay_run(id) ON DELETE CASCADE
);

CREATE INDEX idx_pay_run_audit_action ON human.human_pay_run_audit_log(pay_run_id, action);

COMMENT ON TABLE human.human_pay_run_audit_log IS 'Audit trail for pay run lifecycle events.';

