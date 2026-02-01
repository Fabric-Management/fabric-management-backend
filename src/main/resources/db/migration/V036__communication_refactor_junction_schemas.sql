-- ============================================================================
-- V36: Communication Module Refactor - Junction Tables by Schema
-- ============================================================================
-- Moves junction tables from common_communication to owning schemas to break
-- circular dependency. Communication becomes Shared Kernel (Contact, Address only).
-- Drops AddressContact junction; adds contact_phone, contact_email, contact_person to Address.
-- Removes is_whatsapp from Contact (resolve at verification time via API/cache).
-- Removes WORK from address_type (use OFFICE). DB empty - no data migration.
-- ============================================================================

-- ============================================================================
-- 1. Drop junction tables from common_communication (order: FKs first)
-- ============================================================================
DROP TABLE IF EXISTS common_communication.common_address_contact;
DROP TABLE IF EXISTS common_communication.common_company_address;
DROP TABLE IF EXISTS common_communication.common_company_contact;
DROP TABLE IF EXISTS common_communication.common_user_address;
DROP TABLE IF EXISTS common_communication.common_user_contact;

-- ============================================================================
-- 2. Junction tables in common_company schema
-- ============================================================================
CREATE TABLE common_company.common_company_contact (
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

    PRIMARY KEY (company_id, contact_id),
    CONSTRAINT fk_company_contact_company FOREIGN KEY (company_id)
        REFERENCES common_company.common_company(id) ON DELETE CASCADE,
    CONSTRAINT fk_company_contact_contact FOREIGN KEY (contact_id)
        REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_company_contact_company ON common_company.common_company_contact(company_id);
CREATE INDEX idx_company_contact_contact ON common_company.common_company_contact(contact_id);
CREATE INDEX idx_company_contact_tenant ON common_company.common_company_contact(tenant_id);
CREATE INDEX idx_company_contact_department ON common_company.common_company_contact(department) WHERE department IS NOT NULL;

COMMENT ON TABLE common_company.common_company_contact IS 'Junction: Company ↔ Contact. Company module owns this table.';

CREATE TABLE common_company.common_company_address (
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

    PRIMARY KEY (company_id, address_id),
    CONSTRAINT fk_company_address_company FOREIGN KEY (company_id)
        REFERENCES common_company.common_company(id) ON DELETE CASCADE,
    CONSTRAINT fk_company_address_address FOREIGN KEY (address_id)
        REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);

CREATE INDEX idx_company_address_company ON common_company.common_company_address(company_id);
CREATE INDEX idx_company_address_address ON common_company.common_company_address(address_id);
CREATE INDEX idx_company_address_tenant ON common_company.common_company_address(tenant_id);
CREATE INDEX idx_company_address_hq ON common_company.common_company_address(is_headquarters) WHERE is_headquarters = TRUE;

COMMENT ON TABLE common_company.common_company_address IS 'Junction: Company ↔ Address. Company module owns this table.';

-- ============================================================================
-- 3. Junction tables in common_user schema
-- ============================================================================
CREATE TABLE common_user.common_user_contact (
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,

    user_id UUID NOT NULL,
    contact_id UUID NOT NULL,

    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (user_id, contact_id),
    CONSTRAINT fk_user_contact_user FOREIGN KEY (user_id)
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_contact_contact FOREIGN KEY (contact_id)
        REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_contact_user ON common_user.common_user_contact(user_id);
CREATE INDEX idx_user_contact_contact ON common_user.common_user_contact(contact_id);
CREATE INDEX idx_user_contact_tenant ON common_user.common_user_contact(tenant_id);

COMMENT ON TABLE common_user.common_user_contact IS 'Junction: User ↔ Contact. User module owns this table.';

CREATE TABLE common_user.common_user_address (
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

    PRIMARY KEY (user_id, address_id),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id)
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_address_address FOREIGN KEY (address_id)
        REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_address_user ON common_user.common_user_address(user_id);
CREATE INDEX idx_user_address_address ON common_user.common_user_address(address_id);
CREATE INDEX idx_user_address_tenant ON common_user.common_user_address(tenant_id);

COMMENT ON TABLE common_user.common_user_address IS 'Junction: User ↔ Address. User module owns this table.';

-- ============================================================================
-- 4. Address: add optional contact fields (replaces AddressContact junction)
-- ============================================================================
ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50);

ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255);

ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS contact_person VARCHAR(100);

COMMENT ON COLUMN common_communication.common_address.contact_phone IS 'Depo/şube telefonu - optional location contact';
COMMENT ON COLUMN common_communication.common_address.contact_email IS 'Depo/şube email - optional location contact';
COMMENT ON COLUMN common_communication.common_address.contact_person IS 'İrtibat kişisi - optional contact person for address';

-- ============================================================================
-- 5. Contact: remove is_whatsapp (resolve channel at verification time)
-- ============================================================================
ALTER TABLE common_communication.common_contact
DROP CONSTRAINT IF EXISTS chk_contact_whatsapp_mobile;

ALTER TABLE common_communication.common_contact
DROP COLUMN IF EXISTS is_whatsapp;

-- ============================================================================
-- 6. Address type: remove WORK from CHECK (use OFFICE)
-- ============================================================================
ALTER TABLE common_communication.common_address
DROP CONSTRAINT IF EXISTS chk_address_type;

ALTER TABLE common_communication.common_address
ADD CONSTRAINT chk_address_type CHECK (address_type IN (
    'HOME', 'BILLING', 'MAILING', 'TEMPORARY', 'ALTERNATE',
    'OFFICE', 'HEADQUARTERS', 'BRANCH', 'WAREHOUSE', 'FACTORY', 'SHIPPING',
    'WORKSITE', 'REMOTE'
));
