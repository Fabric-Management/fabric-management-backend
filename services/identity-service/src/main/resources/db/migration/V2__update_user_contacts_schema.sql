-- =====================================================
-- Identity Service Database Schema Update
-- Version: V2
-- Description: Update user_contacts table for comprehensive contact management
-- =====================================================

-- Drop existing user_contacts table and recreate with new schema
DROP TABLE IF EXISTS user_contacts CASCADE;

-- Create new user_contacts table with comprehensive contact management
CREATE TABLE IF NOT EXISTS user_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id UUID NOT NULL,
    job_title VARCHAR(100),
    department VARCHAR(100),
    time_zone VARCHAR(50),
    language_preference VARCHAR(10),
    preferred_contact_method VARCHAR(20),
    contact_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    primary_email VARCHAR(255),
    primary_phone VARCHAR(50),
    primary_address VARCHAR(500),
    tenant_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    INDEX idx_user_contact_user_id (user_id),
    INDEX idx_user_contact_tenant_user (tenant_id, user_id),
    INDEX idx_user_contact_status (status),
    INDEX idx_user_contact_job_title (job_title),
    INDEX idx_user_contact_department (department),
    INDEX idx_user_contact_primary_email (primary_email),
    INDEX idx_user_contact_primary_phone (primary_phone),
    INDEX idx_user_contact_is_active (is_active),
    INDEX idx_user_contact_is_deleted (is_deleted)
);

-- Add constraints
ALTER TABLE user_contacts 
ADD CONSTRAINT chk_contact_type CHECK (contact_type IN ('USER', 'COMPANY')),
ADD CONSTRAINT chk_contact_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'SUSPENDED'));

-- Comments for documentation
COMMENT ON TABLE user_contacts IS 'Comprehensive user contact management table';
COMMENT ON COLUMN user_contacts.job_title IS 'User job title';
COMMENT ON COLUMN user_contacts.department IS 'User department';
COMMENT ON COLUMN user_contacts.time_zone IS 'User time zone';
COMMENT ON COLUMN user_contacts.language_preference IS 'User language preference';
COMMENT ON COLUMN user_contacts.preferred_contact_method IS 'Preferred contact method';
COMMENT ON COLUMN user_contacts.contact_type IS 'Type of contact (USER/COMPANY)';
COMMENT ON COLUMN user_contacts.status IS 'Contact status';
COMMENT ON COLUMN user_contacts.primary_email IS 'Primary email address';
COMMENT ON COLUMN user_contacts.primary_phone IS 'Primary phone number';
COMMENT ON COLUMN user_contacts.primary_address IS 'Primary address';
COMMENT ON COLUMN user_contacts.tenant_id IS 'Tenant ID for multi-tenancy';
COMMENT ON COLUMN user_contacts.is_active IS 'Whether contact is active';
