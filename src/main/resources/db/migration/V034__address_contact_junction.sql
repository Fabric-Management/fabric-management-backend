-- ============================================================================
-- V34: Address-Contact Junction Table
-- ============================================================================
-- Establishes relationship between Address and Contact entities
-- Supports address-specific contacts for both Company and User addresses
-- Each address can have multiple contacts (phone, email, fax, etc.)
-- Last Updated: 2025-01-27
-- ============================================================================

-- ============================================================================
-- TABLE: common_address_contact (Junction table - Composite Primary Key)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_address_contact (
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    address_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(100),  -- e.g., "Main Phone", "Reception", "Emergency Contact"
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    PRIMARY KEY (address_id, contact_id),
    CONSTRAINT fk_address_contact_address FOREIGN KEY (address_id) 
        REFERENCES common_communication.common_address(id) ON DELETE CASCADE,
    CONSTRAINT fk_address_contact_contact FOREIGN KEY (contact_id) 
        REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_address_contact_address ON common_communication.common_address_contact(address_id);
CREATE INDEX idx_address_contact_contact ON common_communication.common_address_contact(contact_id);
CREATE INDEX idx_address_contact_tenant ON common_communication.common_address_contact(tenant_id);
CREATE INDEX idx_address_contact_primary ON common_communication.common_address_contact(address_id, is_primary) WHERE is_primary = TRUE;

COMMENT ON TABLE common_communication.common_address_contact IS 'Junction table: Address ↔ Contact relationship. Links contacts to specific addresses (supports both company and user addresses).';
COMMENT ON COLUMN common_communication.common_address_contact.is_primary IS 'true = primary contact for this address (one per address)';
COMMENT ON COLUMN common_communication.common_address_contact.label IS 'Optional label for contact (e.g., "Main Phone", "Reception", "Emergency Contact")';

