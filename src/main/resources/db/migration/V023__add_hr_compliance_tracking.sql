-- ============================================================================
-- V23: HR Compliance Tracking - Employee Record Completeness
-- ============================================================================
-- Adds HR compliance tracking fields to human_employee table
-- Tracks completeness of recommended HR fields for compliance reporting
-- Last Updated: 2025-11-04
-- ============================================================================

-- ============================================================================
-- ADD COLUMNS: HR Compliance Tracking
-- ============================================================================
ALTER TABLE human.human_employee
    ADD COLUMN IF NOT EXISTS hr_compliance_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS missing_fields VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_compliance_check_at TIMESTAMP;

-- Index for compliance status queries
CREATE INDEX IF NOT EXISTS idx_employee_compliance_status 
    ON human.human_employee(tenant_id, hr_compliance_status) 
    WHERE hr_compliance_status IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employee_compliance_check 
    ON human.human_employee(tenant_id, last_compliance_check_at) 
    WHERE last_compliance_check_at IS NOT NULL;

-- Comments
COMMENT ON COLUMN human.human_employee.hr_compliance_status IS 'HR Compliance Status: COMPLETE, MISSING_RECOMMENDED, MISSING_REQUIRED';
COMMENT ON COLUMN human.human_employee.missing_fields IS 'Comma-separated list of missing recommended HR fields (e.g., "employeeNumber,hireDate")';
COMMENT ON COLUMN human.human_employee.last_compliance_check_at IS 'Last timestamp when compliance status was checked and updated';

