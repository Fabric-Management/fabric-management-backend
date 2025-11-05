-- ============================================================================
-- V24: Employee Number Sequence Table - Optimized Employee Number Generation
-- ============================================================================
-- Creates sequence table for auto-incrementing employee numbers per tenant
-- 
-- Purpose:
-- - Optimize employee number generation (avoid full table scans)
-- - Atomic sequence increment using SELECT FOR UPDATE
-- - Global sequence (not year-based) to avoid reset issues
-- 
-- Format: {TENANT_UID}-EMP-{SEQUENCE}
-- Example: "ACME-001-EMP-00042"
-- 
-- Design Decision:
-- - Global sequence (not year-based) to avoid:
--   * Sequence reset at year boundary
--   * Duplicate sequence numbers across years
--   * Sequence exhaustion in large companies
-- 
-- Year information available from Employee.hireDate if needed.
-- 
-- Last Updated: 2025-11-05
-- ============================================================================

-- ============================================================================
-- TABLE: human_employee_number_sequence
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_employee_number_sequence (
    tenant_id UUID PRIMARY KEY,
    next_sequence INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique index (already primary key, but explicit for clarity)
CREATE UNIQUE INDEX IF NOT EXISTS idx_emp_seq_tenant 
    ON human.human_employee_number_sequence(tenant_id);

-- Comments
COMMENT ON TABLE human.human_employee_number_sequence IS 
    'Tracks auto-incrementing employee numbers per tenant. Uses pessimistic locking (SELECT FOR UPDATE) for atomic increments.';

COMMENT ON COLUMN human.human_employee_number_sequence.tenant_id IS 
    'Tenant ID (primary key) - one sequence per tenant';

COMMENT ON COLUMN human.human_employee_number_sequence.next_sequence IS 
    'Next sequence number to assign (auto-increments)';

COMMENT ON COLUMN human.human_employee_number_sequence.updated_at IS 
    'Last update timestamp for sequence increment';

