-- =====================================================
-- User Service Database Migration
-- Version: V2
-- Description: Create lightweight user tables focused on authentication/identity
-- Contact information is managed separately in contact-service
-- =====================================================

-- Users table - focused on authentication and identity only
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Core identity fields
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    -- Role and status
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Email verification
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token VARCHAR(100),
    email_verification_sent_at TIMESTAMP,

    -- Password reset
    password_reset_token VARCHAR(100),
    password_reset_token_expires_at TIMESTAMP,
    password_changed_at TIMESTAMP,

    -- Two-factor authentication
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_secret VARCHAR(100),

    -- Account security
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_role CHECK (role IN ('USER', 'ADMIN', 'SUPER_ADMIN')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

-- Indexes for performance
CREATE INDEX idx_user_username ON users(username);
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_tenant_id ON users(tenant_id);
CREATE INDEX idx_user_status ON users(status);
CREATE INDEX idx_user_role ON users(role);
CREATE INDEX idx_user_deleted ON users(deleted);
CREATE INDEX idx_user_tenant_status ON users(tenant_id, status);
CREATE INDEX idx_user_email_verified ON users(email_verified);

-- Partial index for active users
CREATE INDEX idx_user_active ON users(id) WHERE deleted = false AND status = 'ACTIVE';

-- Partial index for users with pending email verification
CREATE INDEX idx_user_pending_verification ON users(id) WHERE email_verified = false;

-- Partial index for locked accounts
CREATE INDEX idx_user_locked ON users(id) WHERE locked_until IS NOT NULL;

-- Comments for documentation
COMMENT ON TABLE users IS 'User authentication and identity table. Contact information managed in contact-service';
COMMENT ON COLUMN users.tenant_id IS 'Multi-tenant identifier';
COMMENT ON COLUMN users.username IS 'Unique username for authentication';
COMMENT ON COLUMN users.email IS 'Primary email for authentication and notifications';
COMMENT ON COLUMN users.password_hash IS 'Bcrypt or Argon2 hashed password';
COMMENT ON COLUMN users.role IS 'User role for authorization';
COMMENT ON COLUMN users.status IS 'Account status';
COMMENT ON COLUMN users.email_verified IS 'Email verification status';
COMMENT ON COLUMN users.two_factor_enabled IS 'Two-factor authentication status';
COMMENT ON COLUMN users.failed_login_attempts IS 'Counter for account lockout';
COMMENT ON COLUMN users.locked_until IS 'Account lockout expiration';
COMMENT ON COLUMN users.deleted IS 'Soft delete flag';

-- Function to automatically update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to automatically update updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE
    ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();