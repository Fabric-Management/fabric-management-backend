-- =====================================================
-- Contact Service Database Migration
-- Version: V2
-- Description: Create contact tables for users and companies
-- =====================================================

-- Main contacts table (base for inheritance)
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(20) NOT NULL, -- 'USER' or 'COMPANY'
    contact_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_entity_type CHECK (entity_type IN ('USER', 'COMPANY')),
    CONSTRAINT chk_contact_type CHECK (contact_type IN ('CUSTOMER', 'SUPPLIER', 'PARTNER', 'EMPLOYEE', 'CONTRACTOR', 'PROSPECT')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'BLOCKED', 'ARCHIVED'))
);

-- User contacts table
CREATE TABLE IF NOT EXISTS user_contacts (
    contact_id UUID PRIMARY KEY REFERENCES contacts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL UNIQUE,
    job_title VARCHAR(100),
    department VARCHAR(100),
    linkedin_url VARCHAR(500),
    twitter_handle VARCHAR(50),
    emergency_contact_name VARCHAR(200),
    emergency_contact_phone VARCHAR(50),
    emergency_contact_relationship VARCHAR(100),
    preferred_contact_method VARCHAR(20),
    time_zone VARCHAR(50),
    language_preference VARCHAR(10)
);

-- Company contacts table
CREATE TABLE IF NOT EXISTS company_contacts (
    contact_id UUID PRIMARY KEY REFERENCES contacts(id) ON DELETE CASCADE,
    company_id UUID NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    industry VARCHAR(100),
    company_size VARCHAR(50),
    website VARCHAR(500),
    tax_id VARCHAR(50),
    registration_number VARCHAR(50),
    founded_year INTEGER,
    annual_revenue BIGINT,
    currency_code VARCHAR(3),
    position VARCHAR(100),
    business_unit VARCHAR(100),
    main_contact_person VARCHAR(200),
    main_contact_email VARCHAR(100),
    main_contact_phone VARCHAR(50),
    business_hours VARCHAR(200),
    payment_terms VARCHAR(100),
    credit_limit BIGINT
);

-- Contact emails table
CREATE TABLE IF NOT EXISTS contact_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    email VARCHAR(100) NOT NULL,
    email_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_token VARCHAR(100),
    verification_sent_at TIMESTAMP,
    verified_at TIMESTAMP,
    label VARCHAR(50),
    notes VARCHAR(500),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_email_type CHECK (email_type IN ('PERSONAL', 'WORK', 'BUSINESS', 'BILLING', 'SUPPORT', 'OTHER'))
);

-- Contact phones table
CREATE TABLE IF NOT EXISTS contact_phones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    phone_number VARCHAR(50) NOT NULL,
    country_code VARCHAR(5),
    extension VARCHAR(10),
    phone_type VARCHAR(20) NOT NULL DEFAULT 'MOBILE',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_code VARCHAR(10),
    verification_sent_at TIMESTAMP,
    verified_at TIMESTAMP,
    can_receive_sms BOOLEAN NOT NULL DEFAULT TRUE,
    can_receive_calls BOOLEAN NOT NULL DEFAULT TRUE,
    label VARCHAR(50),
    notes VARCHAR(500),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_phone_type CHECK (phone_type IN ('MOBILE', 'HOME', 'WORK', 'FAX', 'EMERGENCY', 'OTHER'))
);

-- Contact addresses table
CREATE TABLE IF NOT EXISTS contact_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    address_type VARCHAR(20) NOT NULL DEFAULT 'WORK',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    street_address_1 VARCHAR(255) NOT NULL,
    street_address_2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state_province VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    country_code VARCHAR(3),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    label VARCHAR(50),
    notes VARCHAR(500),
    is_validated BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMP,
    validation_provider VARCHAR(50),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_address_type CHECK (address_type IN ('HOME', 'WORK', 'BILLING', 'SHIPPING', 'MAILING', 'HEADQUARTERS', 'BRANCH', 'WAREHOUSE', 'TEMPORARY'))
);

-- Indexes for performance
CREATE INDEX idx_contact_tenant_id ON contacts(tenant_id);
CREATE INDEX idx_contact_type ON contacts(contact_type);
CREATE INDEX idx_contact_status ON contacts(status);
CREATE INDEX idx_contact_deleted ON contacts(deleted);

CREATE INDEX idx_user_contact_user_id ON user_contacts(user_id);
CREATE INDEX idx_user_contact_tenant_user ON user_contacts(contact_id, user_id);

CREATE INDEX idx_company_contact_company_id ON company_contacts(company_id);
CREATE INDEX idx_company_contact_tenant_company ON company_contacts(contact_id, company_id);
CREATE INDEX idx_company_contact_industry ON company_contacts(industry);
CREATE INDEX idx_company_contact_company_name ON company_contacts(company_name);

CREATE INDEX idx_contact_email_contact_id ON contact_emails(contact_id);
CREATE INDEX idx_contact_email_email ON contact_emails(email);
CREATE INDEX idx_contact_email_type ON contact_emails(email_type);
CREATE INDEX idx_contact_email_primary ON contact_emails(is_primary);

CREATE INDEX idx_contact_phone_contact_id ON contact_phones(contact_id);
CREATE INDEX idx_contact_phone_number ON contact_phones(phone_number);
CREATE INDEX idx_contact_phone_type ON contact_phones(phone_type);
CREATE INDEX idx_contact_phone_primary ON contact_phones(is_primary);

CREATE INDEX idx_contact_address_contact_id ON contact_addresses(contact_id);
CREATE INDEX idx_contact_address_type ON contact_addresses(address_type);
CREATE INDEX idx_contact_address_primary ON contact_addresses(is_primary);
CREATE INDEX idx_contact_address_country ON contact_addresses(country);
CREATE INDEX idx_contact_address_postal_code ON contact_addresses(postal_code);

-- Comments for documentation
COMMENT ON TABLE contacts IS 'Base table for all contact entities supporting users and companies';
COMMENT ON TABLE user_contacts IS 'Contact information specific to users';
COMMENT ON TABLE company_contacts IS 'Contact information specific to companies';
COMMENT ON TABLE contact_emails IS 'Email addresses associated with contacts';
COMMENT ON TABLE contact_phones IS 'Phone numbers associated with contacts';
COMMENT ON TABLE contact_addresses IS 'Physical addresses associated with contacts';

COMMENT ON COLUMN contacts.entity_type IS 'Type of entity: USER or COMPANY';
COMMENT ON COLUMN contacts.contact_type IS 'Business relationship type of the contact';
COMMENT ON COLUMN user_contacts.user_id IS 'Reference to user in user-service';
COMMENT ON COLUMN company_contacts.company_id IS 'Reference to company in company-service';