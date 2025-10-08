-- =============================================================================
-- USER SERVICE DATABASE MIGRATION V1
-- =============================================================================
-- Creates all tables for user management service
-- Clean and complete - no patches needed

-- =============================================================================
-- COMMON FUNCTIONS (Idempotent - Self-contained)
-- =============================================================================
-- Each migration defines its own dependencies (Microservice Principle)
-- CREATE OR REPLACE ensures idempotency and no conflicts
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- TABLES
-- =============================================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    registration_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT_REGISTRATION',
    password_hash VARCHAR(255),
    role VARCHAR(50),
    invitation_token VARCHAR(255),
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

-- Password reset tokens table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    reset_method VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts_remaining INTEGER DEFAULT 3,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- User sessions table
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    session_token VARCHAR(500) UNIQUE NOT NULL,
    refresh_token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- User events table (for event sourcing)
CREATE TABLE IF NOT EXISTS user_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    event_version INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT fk_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Note: preferences and settings are stored as JSONB in users table
-- No need for separate tables

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_registration_type ON users (registration_type);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users (deleted);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

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

-- =============================================================================
-- TRIGGERS (Auto-update timestamps)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_set_updated_at_users ON users;
CREATE TRIGGER trg_set_updated_at_users
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_set_updated_at_sessions ON user_sessions;
CREATE TRIGGER trg_set_updated_at_sessions
  BEFORE UPDATE ON user_sessions
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_set_updated_at_reset_tokens ON password_reset_tokens;
CREATE TRIGGER trg_set_updated_at_reset_tokens
  BEFORE UPDATE ON password_reset_tokens
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- OUTBOX PATTERN TABLE (For reliable event publishing)
-- =============================================================================
-- Service-specific outbox to prevent table name conflicts
CREATE TABLE IF NOT EXISTS user_outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    tenant_id VARCHAR(100),
    topic VARCHAR(100) NOT NULL
);

-- Outbox indexes
CREATE INDEX IF NOT EXISTS idx_user_outbox_processed ON user_outbox_events (processed, created_at);
CREATE INDEX IF NOT EXISTS idx_user_outbox_aggregate ON user_outbox_events (aggregate_type, aggregate_id);

-- =============================================================================
-- SEED DATA - DEFAULT SUPER ADMIN
-- =============================================================================
-- Default Super Admin for initial system access
-- Email: admin@system.local
-- Password: Admin@123
-- Note: Change password immediately after first login in production!

INSERT INTO users (
    id,
    tenant_id,
    first_name,
    last_name,
    display_name,
    status,
    role,
    password_hash,
    registration_type,
    created_by,
    updated_by
) VALUES (
    '00000000-0000-0000-0000-000000000001'::UUID,
    '00000000-0000-0000-0000-000000000000'::UUID,  -- Default tenant ID
    'Super',
    'Admin',
    'Super Administrator',
    'ACTIVE',
    'SUPER_ADMIN',
    '$2b$12$9fPRsXGTeGgJ0kgRwEBG1OmVXKwpdniaEcBRg0vgdBGAynJFRkIaS',  -- bcrypt hash of 'Admin@123'
    'SYSTEM_CREATED',
    'SYSTEM',
    'SYSTEM'
) ON CONFLICT (id) DO NOTHING;