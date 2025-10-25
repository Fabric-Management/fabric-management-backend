-- ============================================================================
-- V4: Auth Module Tables
-- ============================================================================
-- Authentication, verification codes, refresh tokens
-- Last Updated: 2025-10-25
-- ============================================================================

-- ============================================================================
-- TABLE: common_auth_user
-- ============================================================================
CREATE TABLE common_auth.common_auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    contact_value VARCHAR(255) UNIQUE NOT NULL,
    contact_type VARCHAR(20) NOT NULL,
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
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_auth_contact ON common_auth.common_auth_user(contact_value);
CREATE INDEX idx_auth_verified ON common_auth.common_auth_user(is_verified) WHERE is_verified = TRUE;
CREATE INDEX idx_auth_locked ON common_auth.common_auth_user(locked_until) WHERE locked_until > CURRENT_TIMESTAMP;

COMMENT ON TABLE common_auth.common_auth_user IS 'Authentication credentials - BCrypt password hashing';
COMMENT ON COLUMN common_auth.common_auth_user.password_hash IS 'BCrypt hash (strength 10)';
COMMENT ON COLUMN common_auth.common_auth_user.failed_login_attempts IS 'Locks account after 5 attempts for 30 minutes';

-- ============================================================================
-- TABLE: common_refresh_token
-- ============================================================================
CREATE TABLE common_auth.common_refresh_token (
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

CREATE UNIQUE INDEX idx_refresh_token ON common_auth.common_refresh_token(token);
CREATE INDEX idx_refresh_user ON common_auth.common_refresh_token(user_id);
CREATE INDEX idx_refresh_expires ON common_auth.common_refresh_token(expires_at);

COMMENT ON TABLE common_auth.common_refresh_token IS 'Refresh tokens for JWT token refresh flow';
COMMENT ON COLUMN common_auth.common_refresh_token.expires_at IS 'Default: 7 days';

-- ============================================================================
-- TABLE: common_verification_code
-- ============================================================================
CREATE TABLE common_auth.common_verification_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    contact_value VARCHAR(255) NOT NULL,
    code VARCHAR(10) NOT NULL,
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

CREATE INDEX idx_verification_contact ON common_auth.common_verification_code(contact_value);
CREATE INDEX idx_verification_code ON common_auth.common_verification_code(code);
CREATE INDEX idx_verification_expires ON common_auth.common_verification_code(expires_at);

COMMENT ON TABLE common_auth.common_verification_code IS 'Verification codes for registration, password reset';
COMMENT ON COLUMN common_auth.common_verification_code.code IS '6-digit code';
COMMENT ON COLUMN common_auth.common_verification_code.type IS 'REGISTRATION, PASSWORD_RESET, EMAIL_VERIFICATION, PHONE_VERIFICATION';
COMMENT ON COLUMN common_auth.common_verification_code.expires_at IS 'Default: 10 minutes';
COMMENT ON COLUMN common_auth.common_verification_code.attempt_count IS 'Max 3 attempts';

