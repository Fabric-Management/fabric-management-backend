-- ============================================================================
-- V16: Contact & Address Management - Communication Infrastructure
-- ============================================================================
-- Establishes normalized contact and address management for User and Company
-- Supports multiple contacts/addresses per entity with modern communication channels
-- Last Updated: 2025-11-01
-- ============================================================================

-- ============================================================================
-- SCHEMA: common_communication
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS common_communication;

CREATE SEQUENCE IF NOT EXISTS common_communication.seq_contact START 1000;
CREATE SEQUENCE IF NOT EXISTS common_communication.seq_address START 1000;

COMMENT ON SCHEMA common_communication IS 'Communication infrastructure: contacts, addresses, notifications';

-- ============================================================================
-- TABLE: common_contact
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    contact_value VARCHAR(255) NOT NULL,
    contact_type VARCHAR(50) NOT NULL,
    
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(100),
    parent_contact_id UUID,
    is_personal BOOLEAN NOT NULL DEFAULT TRUE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_contact_parent FOREIGN KEY (parent_contact_id) 
        REFERENCES common_communication.common_contact(id) ON DELETE SET NULL,
    CONSTRAINT chk_contact_type CHECK (contact_type IN ('EMAIL', 'PHONE', 'PHONE_EXTENSION', 'FAX', 'WEBSITE', 'WHATSAPP', 'SOCIAL_MEDIA'))
);

CREATE INDEX idx_contact_value ON common_communication.common_contact(contact_value);
CREATE INDEX idx_contact_type ON common_communication.common_contact(contact_type);
CREATE INDEX idx_contact_parent ON common_communication.common_contact(parent_contact_id);
CREATE INDEX idx_contact_tenant ON common_communication.common_contact(tenant_id);
CREATE INDEX idx_contact_verified ON common_communication.common_contact(is_verified) WHERE is_verified = TRUE;
CREATE INDEX idx_contact_primary ON common_communication.common_contact(is_primary) WHERE is_primary = TRUE;

COMMENT ON TABLE common_communication.common_contact IS 'Generic contact information (email, phone, WhatsApp, etc.) for User and Company';
COMMENT ON COLUMN common_communication.common_contact.contact_value IS 'Contact value: email address, phone number (E.164), extension number, URL, etc.';
COMMENT ON COLUMN common_communication.common_contact.contact_type IS 'Contact type: EMAIL, PHONE, PHONE_EXTENSION, FAX, WEBSITE, WHATSAPP, SOCIAL_MEDIA';
COMMENT ON COLUMN common_communication.common_contact.parent_contact_id IS 'For PHONE_EXTENSION: references parent PHONE contact';
COMMENT ON COLUMN common_communication.common_contact.is_personal IS 'true = User personal contact, false = Company-provided contact';

-- ============================================================================
-- TABLE: common_address
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    street_address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) NOT NULL,
    address_type VARCHAR(50) NOT NULL,
    
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(100),
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_address_type CHECK (address_type IN ('HOME', 'WORK', 'HEADQUARTERS', 'BRANCH', 'WAREHOUSE', 'SHIPPING', 'BILLING'))
);

CREATE INDEX idx_address_type ON common_communication.common_address(address_type);
CREATE INDEX idx_address_city ON common_communication.common_address(city);
CREATE INDEX idx_address_country ON common_communication.common_address(country);
CREATE INDEX idx_address_tenant ON common_communication.common_address(tenant_id);
CREATE INDEX idx_address_primary ON common_communication.common_address(is_primary) WHERE is_primary = TRUE;

COMMENT ON TABLE common_communication.common_address IS 'Generic address information for User and Company';
COMMENT ON COLUMN common_communication.common_address.address_type IS 'Address type: HOME, WORK, HEADQUARTERS, BRANCH, WAREHOUSE, SHIPPING, BILLING';

-- ============================================================================
-- TABLE: common_user_contact
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_user_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    user_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_for_authentication BOOLEAN NOT NULL DEFAULT FALSE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_user_contact UNIQUE(user_id, contact_id),
    CONSTRAINT fk_user_contact_user FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_contact_contact FOREIGN KEY (contact_id) 
        REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_contact_user ON common_communication.common_user_contact(user_id);
CREATE INDEX idx_user_contact_contact ON common_communication.common_user_contact(contact_id);
CREATE INDEX idx_user_contact_tenant ON common_communication.common_user_contact(tenant_id);
CREATE INDEX idx_user_contact_auth ON common_communication.common_user_contact(is_for_authentication) WHERE is_for_authentication = TRUE;

COMMENT ON TABLE common_communication.common_user_contact IS 'Junction table: User ↔ Contact relationship';
COMMENT ON COLUMN common_communication.common_user_contact.is_for_authentication IS 'true = this contact can be used for login/authentication';

-- ============================================================================
-- TABLE: common_company_contact
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_company_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    company_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    department VARCHAR(100),
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_company_contact UNIQUE(company_id, contact_id),
    CONSTRAINT fk_company_contact_company FOREIGN KEY (company_id) 
        REFERENCES common_company.common_company(id) ON DELETE CASCADE,
    CONSTRAINT fk_company_contact_contact FOREIGN KEY (contact_id) 
        REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_company_contact_company ON common_communication.common_company_contact(company_id);
CREATE INDEX idx_company_contact_contact ON common_communication.common_company_contact(contact_id);
CREATE INDEX idx_company_contact_tenant ON common_communication.common_company_contact(tenant_id);
CREATE INDEX idx_company_contact_department ON common_communication.common_company_contact(department) WHERE department IS NOT NULL;

COMMENT ON TABLE common_communication.common_company_contact IS 'Junction table: Company ↔ Contact relationship';
COMMENT ON COLUMN common_communication.common_company_contact.department IS 'Department-specific contact (e.g., "Sales", "Support"), null = company-wide';

-- ============================================================================
-- TABLE: common_user_address
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_user_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    user_id UUID NOT NULL,
    address_id UUID NOT NULL,
    
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_work_address BOOLEAN NOT NULL DEFAULT FALSE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_user_address UNIQUE(user_id, address_id),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_address_address FOREIGN KEY (address_id) 
        REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_address_user ON common_communication.common_user_address(user_id);
CREATE INDEX idx_user_address_address ON common_communication.common_user_address(address_id);
CREATE INDEX idx_user_address_tenant ON common_communication.common_user_address(tenant_id);

COMMENT ON TABLE common_communication.common_user_address IS 'Junction table: User ↔ Address relationship';
COMMENT ON COLUMN common_communication.common_user_address.is_work_address IS 'true = work/office address (independent from company address)';

-- ============================================================================
-- TABLE: common_company_address
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_company_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    company_id UUID NOT NULL,
    address_id UUID NOT NULL,
    
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_headquarters BOOLEAN NOT NULL DEFAULT FALSE,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_company_address UNIQUE(company_id, address_id),
    CONSTRAINT fk_company_address_company FOREIGN KEY (company_id) 
        REFERENCES common_company.common_company(id) ON DELETE CASCADE,
    CONSTRAINT fk_company_address_address FOREIGN KEY (address_id) 
        REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);

CREATE INDEX idx_company_address_company ON common_communication.common_company_address(company_id);
CREATE INDEX idx_company_address_address ON common_communication.common_company_address(address_id);
CREATE INDEX idx_company_address_tenant ON common_communication.common_company_address(tenant_id);
CREATE INDEX idx_company_address_hq ON common_communication.common_company_address(is_headquarters) WHERE is_headquarters = TRUE;

COMMENT ON TABLE common_communication.common_company_address IS 'Junction table: Company ↔ Address relationship';
COMMENT ON COLUMN common_communication.common_company_address.is_headquarters IS 'true = company headquarters location';

