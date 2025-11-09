-- ============================================================================
-- V031: Leave Domain Persistence
-- ----------------------------------------------------------------------------
-- Creates core tables for leave types, balances, accrual logs, and holiday
-- calendars. Supports multi-tenant operations with localization.
-- Last Updated: 2025-11-09
-- ============================================================================

-- ============================================================================
-- TABLE: human_leave_type
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
CREATE INDEX idx_leave_type_active ON human.human_leave_type(tenant_id, is_active);

COMMENT ON TABLE human.human_leave_type IS 'Master data for leave types per tenant and country.';

-- ============================================================================
-- TABLE: human_leave_balance
-- ============================================================================
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_leave_balance UNIQUE (tenant_id, employee_id, leave_type_id),
    CONSTRAINT fk_leave_balance_type FOREIGN KEY (leave_type_id)
        REFERENCES human.human_leave_type(id) ON DELETE CASCADE
);

CREATE INDEX idx_leave_balance_employee ON human.human_leave_balance(tenant_id, employee_id);
CREATE INDEX idx_leave_balance_country ON human.human_leave_balance(tenant_id, country_code);

COMMENT ON TABLE human.human_leave_balance IS 'Tracks leave balances per employee and leave type.';

-- ============================================================================
-- TABLE: human_leave_accrual_log
-- ============================================================================
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_leave_accrual_type FOREIGN KEY (leave_type_id)
        REFERENCES human.human_leave_type(id) ON DELETE CASCADE
);

CREATE INDEX idx_leave_accrual_employee ON human.human_leave_accrual_log(tenant_id, employee_id);
CREATE INDEX idx_leave_accrual_type ON human.human_leave_accrual_log(tenant_id, leave_type_id);

COMMENT ON TABLE human.human_leave_accrual_log IS 'Historical accrual events per employee and leave type.';

-- ============================================================================
-- TABLE: human_holiday_calendar
-- ============================================================================
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
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_holiday_calendar UNIQUE (tenant_id, country_code, calendar_year)
);

CREATE INDEX idx_holiday_calendar_country ON human.human_holiday_calendar(tenant_id, country_code);

COMMENT ON TABLE human.human_holiday_calendar IS 'Stores effective-dated holiday calendars per country.';

