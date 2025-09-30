-- =============================================================================
-- USER SERVICE DATABASE MIGRATION
-- =============================================================================
-- Creates tables for user management service

-- Users table (Updated for contact-based login)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    display_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING_VERIFICATION',
    registration_type VARCHAR(20) DEFAULT 'DIRECT_REGISTRATION',
    password_hash VARCHAR(255),
    role VARCHAR(50),
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    preferences JSONB,
    settings JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- User contacts table (for multi-contact login)
CREATE TABLE IF NOT EXISTS user_contacts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    contact_type VARCHAR(10) NOT NULL, -- EMAIL or PHONE
    is_verified BOOLEAN DEFAULT FALSE,
    is_primary BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_contact_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_contact UNIQUE (user_id, contact_value)
);

-- Password reset tokens table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    reset_method VARCHAR(20) NOT NULL, -- EMAIL_LINK, SMS_CODE, EMAIL_CODE
    expires_at TIMESTAMP NOT NULL,
    attempts_remaining INTEGER DEFAULT 3,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_reset_token UNIQUE (token)
);

-- User sessions table
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_token VARCHAR(500) UNIQUE NOT NULL,
    refresh_token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- User events table (for event sourcing)
CREATE TABLE IF NOT EXISTS user_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    event_version INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_event_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_registration_type ON users (registration_type);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users (deleted);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON user_contacts (user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_value ON user_contacts (contact_value);
CREATE INDEX IF NOT EXISTS idx_contacts_type ON user_contacts (contact_type);
CREATE INDEX IF NOT EXISTS idx_contacts_verified ON user_contacts (is_verified);
CREATE INDEX IF NOT EXISTS idx_contacts_primary ON user_contacts (is_primary);

CREATE INDEX IF NOT EXISTS idx_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_token ON password_reset_tokens (token);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_contact ON password_reset_tokens (contact_value);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_expires ON password_reset_tokens (expires_at);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_used ON password_reset_tokens (is_used);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON user_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON user_sessions (session_token);
CREATE INDEX IF NOT EXISTS idx_sessions_active ON user_sessions (is_active);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON user_sessions (expires_at);

CREATE INDEX IF NOT EXISTS idx_events_user_id ON user_events (user_id);
CREATE INDEX IF NOT EXISTS idx_events_type ON user_events (event_type);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON user_events (created_at);

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_set_updated_at_users ON users;
CREATE TRIGGER trg_set_updated_at_users
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_set_updated_at_sessions ON user_sessions;
CREATE TRIGGER trg_set_updated_at_sessions
  BEFORE UPDATE ON user_sessions
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();
