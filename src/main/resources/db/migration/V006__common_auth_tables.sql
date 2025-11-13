-- ============================================================================
-- V6: Auth Module Tables
-- ============================================================================
-- Authentication, verification codes, refresh tokens
-- User-based authentication (one AuthUser per User)
-- Multi-contact login supported via UserContact junction
-- Last Updated: 2025-11-13 (User-based authentication refactor)
-- ============================================================================

-- ============================================================================
-- TABLE: common_auth_user
-- ============================================================================
CREATE TABLE common_auth.common_auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    -- User-based authentication (one AuthUser per User)
    user_id UUID NOT NULL UNIQUE,
    
    -- Deprecated fields (kept for backward compatibility, will be removed in future)
    contact_value VARCHAR(255),
    contact_type VARCHAR(20),
    contact_id UUID,  -- DEPRECATED: Kept for backward compatibility
    
    password_hash VARCHAR(255) NOT NULL,
    
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_auth_user_user 
        FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_auth_verified ON common_auth.common_auth_user(is_verified) WHERE is_verified = TRUE;
CREATE INDEX idx_auth_locked ON common_auth.common_auth_user(locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX idx_auth_user_id ON common_auth.common_auth_user(user_id);
CREATE INDEX idx_auth_contact_id ON common_auth.common_auth_user(contact_id);

COMMENT ON TABLE common_auth.common_auth_user IS 
    'Authentication credentials - User-based (one AuthUser per User). Multi-contact login supported via UserContact junction.';
COMMENT ON COLUMN common_auth.common_auth_user.user_id IS 
    'References User entity. One AuthUser per User. Any verified contact of this User can be used for login.';
COMMENT ON COLUMN common_auth.common_auth_user.contact_value IS 'DEPRECATED: Use user_id and UserContact junction instead. Will be dropped in future migration.';
COMMENT ON COLUMN common_auth.common_auth_user.contact_type IS 'DEPRECATED: Use user_id and UserContact junction instead. Will be dropped in future migration.';
COMMENT ON COLUMN common_auth.common_auth_user.contact_id IS 'DEPRECATED: Kept for backward compatibility. Use user_id instead. Will be removed in future migration.';
COMMENT ON COLUMN common_auth.common_auth_user.password_hash IS 'BCrypt hash (strength 10)';
COMMENT ON COLUMN common_auth.common_auth_user.failed_login_attempts IS 'Locks account after 5 attempts for 30 minutes';

-- ============================================================================
-- TABLE: common_refresh_token
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_auth.common_refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token ON common_auth.common_refresh_token(token);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON common_auth.common_refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_expires ON common_auth.common_refresh_token(expires_at);

COMMENT ON TABLE common_auth.common_refresh_token IS 'Refresh tokens for JWT token refresh flow';
COMMENT ON COLUMN common_auth.common_refresh_token.expires_at IS 'Default: 7 days';

-- ============================================================================
-- TABLE: common_verification_code
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_auth.common_verification_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    contact_value VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_verification_contact ON common_auth.common_verification_code(contact_value);
CREATE INDEX IF NOT EXISTS idx_verification_contact_type ON common_auth.common_verification_code(tenant_id, contact_value, type);
CREATE INDEX IF NOT EXISTS idx_verification_expires ON common_auth.common_verification_code(expires_at);

COMMENT ON TABLE common_auth.common_verification_code IS 'Verification codes for registration, password reset';
COMMENT ON COLUMN common_auth.common_verification_code.code_hash IS 'BCrypt hash of verification code (single use)';
COMMENT ON COLUMN common_auth.common_verification_code.type IS 'REGISTRATION, PASSWORD_RESET, EMAIL_VERIFICATION, PHONE_VERIFICATION';
COMMENT ON COLUMN common_auth.common_verification_code.expires_at IS 'Default: 10 minutes';
COMMENT ON COLUMN common_auth.common_verification_code.attempt_count IS 'Max 3 attempts';

-- ============================================================================
-- TABLE: common_registration_token
-- ============================================================================
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
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
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

