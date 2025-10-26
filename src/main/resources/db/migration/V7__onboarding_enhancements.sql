-- =====================================================
-- V7: ONBOARDING ENHANCEMENTS
-- =====================================================
-- Purpose: Add token-based onboarding and user onboarding tracking
-- Date: 2025-10-25
-- =====================================================

-- Add onboarding tracking to users
ALTER TABLE common_user.common_user
ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;

COMMENT ON COLUMN common_user.common_user.onboarding_completed_at IS 'Timestamp when user completed onboarding wizard';

-- Registration tokens for secure email-based onboarding
CREATE TABLE IF NOT EXISTS common_auth.common_registration_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    token VARCHAR(36) UNIQUE NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    token_type VARCHAR(20) NOT NULL CHECK (token_type IN ('SALES_LED', 'SELF_SERVICE')),
    
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    
    user_id UUID,
    company_id UUID,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_registration_token_token ON common_auth.common_registration_token(token);
CREATE INDEX IF NOT EXISTS idx_registration_token_contact ON common_auth.common_registration_token(contact_value);
CREATE INDEX IF NOT EXISTS idx_registration_token_valid ON common_auth.common_registration_token(is_used, expires_at)
    WHERE is_used = FALSE;

COMMENT ON TABLE common_auth.common_registration_token IS 'Secure tokens for email-based registration flows';
COMMENT ON COLUMN common_auth.common_registration_token.token IS 'UUID token sent via email';
COMMENT ON COLUMN common_auth.common_registration_token.token_type IS 'SALES_LED (token only) or SELF_SERVICE (token + code)';
COMMENT ON COLUMN common_auth.common_registration_token.expires_at IS '24-hour expiry for security';

