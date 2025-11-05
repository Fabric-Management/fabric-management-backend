-- ============================================================================
-- V21: Human Resources Module - Employee Entity
-- ============================================================================
-- Creates Employee entity for HR/İK data (One-to-One with User)
-- 
-- CRITICAL SEPARATION:
-- - User = Authentication, basic identity, platform access
-- - Employee = HR data, personal info, employment details
-- 
-- Global Support:
-- - Title (Mr, Miss, Mrs, Ms, Dr, Prof, Eng, None)
-- - Gender (MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY)
-- - Birth date for age calculations
-- - Nationality (ISO country code)
-- - Emergency contact
-- 
-- Last Updated: 2025-01-27
-- ============================================================================

-- Create human schema
CREATE SCHEMA IF NOT EXISTS human;

-- Create sequence for employee
CREATE SEQUENCE IF NOT EXISTS human.seq_employee START 1000;

-- ============================================================================
-- TABLE: human_employee
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_employee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- One-to-One relationship with User
    user_id UUID NOT NULL UNIQUE,
    
    -- Personal Information
    title VARCHAR(20), -- MR, MISS, MRS, MS, DR, PROF, ENG, NONE
    gender VARCHAR(20), -- MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY
    birth_date DATE,
    nationality VARCHAR(2), -- ISO 3166-1 alpha-2 (e.g., "TR", "US", "GB")
    
    -- Employment Information
    employee_number VARCHAR(50), -- Company-specific unique identifier
    hire_date DATE,
    termination_date DATE,
    
    -- Emergency Contact (Embedded)
    emergency_contact_name VARCHAR(100),
    emergency_contact_phone VARCHAR(50),
    emergency_contact_relationship VARCHAR(50),
    
    -- Base Entity Fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_employee_user FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_employee_user ON human.human_employee(user_id);
CREATE INDEX idx_employee_tenant ON human.human_employee(tenant_id);
CREATE UNIQUE INDEX idx_employee_employee_number ON human.human_employee(tenant_id, employee_number) 
    WHERE employee_number IS NOT NULL;
CREATE INDEX idx_employee_birth_date ON human.human_employee(birth_date) WHERE birth_date IS NOT NULL;
CREATE INDEX idx_employee_hire_date ON human.human_employee(hire_date) WHERE hire_date IS NOT NULL;
CREATE INDEX idx_employee_active ON human.human_employee(tenant_id, is_active) WHERE is_active = TRUE;

-- Comments
COMMENT ON SCHEMA human IS 'Human Resources module - Employee management, HR data';
COMMENT ON TABLE human.human_employee IS 'Employee entity - HR/İK data for platform users (One-to-One with User)';
COMMENT ON COLUMN human.human_employee.user_id IS 'One-to-One relationship with User entity. Each User can have one Employee record (optional)';
COMMENT ON COLUMN human.human_employee.title IS 'Personal title/salutation: MR, MISS, MRS, MS, DR, PROF, ENG, NONE';
COMMENT ON COLUMN human.human_employee.gender IS 'Gender identity: MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY';
COMMENT ON COLUMN human.human_employee.birth_date IS 'Birth date for age calculations and HR requirements';
COMMENT ON COLUMN human.human_employee.nationality IS 'Nationality (ISO 3166-1 alpha-2 country code): TR, US, GB, DE, etc.';
COMMENT ON COLUMN human.human_employee.employee_number IS 'Company-specific unique employee identifier (e.g., EMP-001, 2024-001)';
COMMENT ON COLUMN human.human_employee.hire_date IS 'Employment start date';
COMMENT ON COLUMN human.human_employee.termination_date IS 'Employment termination date (NULL if active)';

