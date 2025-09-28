-- =====================================================
-- Identity Service Database Schema
-- Version: V1
-- Description: Modern identity service schema with JWT authentication
-- =====================================================

-- Users table (core identity and authentication)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACTIVATION',

    -- Credentials
    password_hash VARCHAR(255),
    password_created_at TIMESTAMP,
    password_changed_at TIMESTAMP,
    password_must_change BOOLEAN NOT NULL DEFAULT FALSE,

    -- Security
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),

    -- Two-Factor Authentication
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_secret VARCHAR(100),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_role CHECK (role IN ('USER', 'MANAGER', 'ADMIN', 'SUPER_ADMIN')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'LOCKED'))
);

-- User contacts table (part of user aggregate)
CREATE TABLE user_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_type VARCHAR(20) NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMP,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_contact_type CHECK (contact_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT chk_contact_status CHECK (status IN ('PENDING', 'VERIFIED', 'SUSPENDED', 'INACTIVE')),
    CONSTRAINT uk_contact_value UNIQUE (contact_value)
);

-- Verification tokens table (temporary storage)
CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES user_contacts(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL,
    code VARCHAR(10) NOT NULL,
    token_type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_token_type CHECK (token_type IN ('EMAIL_VERIFICATION', 'PHONE_VERIFICATION', 'PASSWORD_RESET'))
);

-- User sessions table (JWT token management)
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_id VARCHAR(255) NOT NULL UNIQUE,
    access_token_hash VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Temporary tokens table (for multi-step flows)
CREATE TABLE temporary_tokens (
    token VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_type VARCHAR(30) NOT NULL,
    data JSON,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_temp_token_type CHECK (token_type IN ('PASSWORD_CREATION', 'TWO_FACTOR', 'PASSWORD_CHANGE'))
);

-- Authentication audit log table
CREATE TABLE authentication_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    contact_used VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_deleted ON users(deleted);
CREATE INDEX idx_users_tenant_status ON users(tenant_id, status);

CREATE INDEX idx_contacts_user_id ON user_contacts(user_id);
CREATE INDEX idx_contacts_value ON user_contacts(contact_value);
CREATE INDEX idx_contacts_type ON user_contacts(contact_type);
CREATE INDEX idx_contacts_status ON user_contacts(status);
CREATE INDEX idx_contacts_primary ON user_contacts(is_primary);

CREATE INDEX idx_tokens_user_id ON verification_tokens(user_id);
CREATE INDEX idx_tokens_contact_id ON verification_tokens(contact_id);
CREATE INDEX idx_tokens_value ON verification_tokens(token);
CREATE INDEX idx_tokens_expires ON verification_tokens(expires_at);

CREATE INDEX idx_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_sessions_token_id ON user_sessions(token_id);
CREATE INDEX idx_sessions_expires ON user_sessions(expires_at);

CREATE INDEX idx_temp_tokens_user_id ON temporary_tokens(user_id);
CREATE INDEX idx_temp_tokens_expires ON temporary_tokens(expires_at);

CREATE INDEX idx_audit_user_id ON authentication_audit_log(user_id);
CREATE INDEX idx_audit_event_type ON authentication_audit_log(event_type);
CREATE INDEX idx_audit_created_at ON authentication_audit_log(created_at);

-- Views for common queries

-- Active users with primary contacts
CREATE VIEW active_users_with_contacts AS
SELECT
    u.id,
    u.tenant_id,
    u.username,
    u.first_name,
    u.last_name,
    u.role,
    u.status,
    u.last_login_at,
    uc.contact_value as primary_contact,
    uc.contact_type as primary_contact_type
FROM users u
LEFT JOIN user_contacts uc ON u.id = uc.user_id AND uc.is_primary = true
WHERE u.deleted = false AND u.status = 'ACTIVE';

-- Comments for documentation
COMMENT ON TABLE users IS 'Core user identity and authentication table';
COMMENT ON TABLE user_contacts IS 'User contact methods (email/phone) for authentication';
COMMENT ON TABLE verification_tokens IS 'Temporary verification tokens for contact verification';
COMMENT ON TABLE user_sessions IS 'Active user sessions with JWT tokens';
COMMENT ON TABLE temporary_tokens IS 'Temporary tokens for multi-step authentication flows';
COMMENT ON TABLE authentication_audit_log IS 'Audit trail for authentication events';

COMMENT ON COLUMN users.status IS 'User account status';
COMMENT ON COLUMN user_contacts.contact_value IS 'Email address or phone number';
COMMENT ON COLUMN user_contacts.is_primary IS 'Primary contact for notifications';
COMMENT ON COLUMN verification_tokens.token IS 'Token for email links';
COMMENT ON COLUMN verification_tokens.code IS 'Short code for SMS verification';
