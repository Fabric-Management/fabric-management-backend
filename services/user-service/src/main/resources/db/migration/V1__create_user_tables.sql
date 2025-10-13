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

-- Note: preferences and settings are stored as JSONB in users table
-- No need for separate tables

-- Removed tables (not needed):
-- - password_reset_tokens: Feature not implemented, using Redis
-- - user_sessions: Using Redis for session management
-- - user_events: Event sourcing not implemented, using Outbox Pattern

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_registration_type ON users (registration_type);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users (deleted);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

-- =============================================================================
-- TRIGGERS (Auto-update timestamps)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_set_updated_at_users ON users;
CREATE TRIGGER trg_set_updated_at_users
  BEFORE UPDATE ON users
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
-- SEED DATA
-- =============================================================================
-- No default users - all users created via tenant onboarding or manual setup