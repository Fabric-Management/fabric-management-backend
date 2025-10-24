-- =============================================================================
-- Notification Service - Initial Schema
-- Version: 1
-- Created: 2025-10-15
-- Author: Fabric Management Team
--
-- Description:
--   Creates notification_configs and notification_logs tables.
--   Supports multi-tenant notification delivery with fallback pattern.
--
-- Tables:
--   1. notification_configs: Tenant-specific SMTP/SMS/WhatsApp credentials
--   2. notification_logs: Delivery tracking and analytics
-- =============================================================================

-- Extension for UUID support
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- Table: notification_configs
-- Purpose: Store tenant-specific notification channel configurations
-- =============================================================================
CREATE TABLE notification_configs (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Tenant Identification
    tenant_id UUID NOT NULL,
    
    -- Channel Configuration
    channel VARCHAR(50) NOT NULL,           -- EMAIL, SMS, WHATSAPP
    provider VARCHAR(50) NOT NULL,          -- SMTP, GMAIL, TWILIO, WHATSAPP_BUSINESS
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    
    -- SMTP (Email) Configuration
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    smtp_username VARCHAR(255),
    smtp_password VARCHAR(500),             -- Encrypted (future: Jasypt/KMS)
    from_email VARCHAR(255),
    from_name VARCHAR(255),
    
    -- SMS/WhatsApp Configuration
    api_key VARCHAR(500),                   -- Encrypted
    from_number VARCHAR(50),                -- E.164 format: +905551234567
    
    -- Priority & Metadata
    priority INTEGER DEFAULT 1,             -- 0=highest (WhatsApp), 1=Email, 2=SMS
    
    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,               -- Optimistic locking
    deleted BOOLEAN NOT NULL DEFAULT false,
    
    -- Constraints
    CONSTRAINT uk_tenant_channel_active UNIQUE (tenant_id, channel, deleted)
);

-- Indexes for performance
CREATE INDEX idx_nc_tenant_channel ON notification_configs(tenant_id, channel);
CREATE INDEX idx_nc_tenant_enabled ON notification_configs(tenant_id, is_enabled);
CREATE INDEX idx_nc_deleted ON notification_configs(deleted) WHERE deleted = false;

-- Comments
COMMENT ON TABLE notification_configs IS 'Tenant-specific notification channel configurations';
COMMENT ON COLUMN notification_configs.tenant_id IS 'Owner tenant UUID';
COMMENT ON COLUMN notification_configs.channel IS 'Notification channel: EMAIL, SMS, WHATSAPP';
COMMENT ON COLUMN notification_configs.provider IS 'Implementation provider: SMTP, GMAIL, TWILIO, etc.';
COMMENT ON COLUMN notification_configs.priority IS 'Channel priority (0=highest, used for fallback order)';
COMMENT ON COLUMN notification_configs.smtp_password IS 'SMTP password (should be encrypted in production)';
COMMENT ON COLUMN notification_configs.api_key IS 'Provider API key (should be encrypted in production)';

-- =============================================================================
-- Table: notification_logs
-- Purpose: Track all notification delivery attempts
-- =============================================================================
CREATE TABLE notification_logs (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Event Tracking
    event_id VARCHAR(255) NOT NULL,         -- Kafka event ID (idempotency)
    tenant_id UUID NOT NULL,
    
    -- Notification Details
    channel VARCHAR(50) NOT NULL,           -- EMAIL, SMS, WHATSAPP
    template VARCHAR(50),                   -- VERIFICATION_CODE, WELCOME, etc.
    recipient VARCHAR(255) NOT NULL,        -- Email or phone number
    subject VARCHAR(500),                   -- Email subject (optional for SMS/WhatsApp)
    body TEXT,                              -- Message body (truncated for logs)
    
    -- Delivery Status
    status VARCHAR(50) NOT NULL,            -- PENDING, PROCESSING, SENT, FAILED, RETRYING, CANCELLED
    error_message TEXT,                     -- Error details if failed
    attempts INTEGER NOT NULL DEFAULT 0,    -- Number of delivery attempts
    sent_at TIMESTAMP,                      -- Timestamp when successfully sent
    
    -- Audit Fields
    triggered_by UUID,                      -- User who triggered the notification
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false
);

-- Indexes for performance and analytics
CREATE INDEX idx_nl_event_id ON notification_logs(event_id);
CREATE INDEX idx_nl_tenant_status ON notification_logs(tenant_id, status);
CREATE INDEX idx_nl_recipient ON notification_logs(recipient);
CREATE INDEX idx_nl_created_at ON notification_logs(created_at);
CREATE INDEX idx_nl_tenant_channel ON notification_logs(tenant_id, channel);
CREATE INDEX idx_nl_status_attempts ON notification_logs(status, attempts) WHERE status = 'FAILED';

-- Comments
COMMENT ON TABLE notification_logs IS 'Notification delivery tracking and analytics';
COMMENT ON COLUMN notification_logs.event_id IS 'Kafka event ID for idempotency check';
COMMENT ON COLUMN notification_logs.status IS 'Delivery status: PENDING, PROCESSING, SENT, FAILED, RETRYING, CANCELLED';
COMMENT ON COLUMN notification_logs.attempts IS 'Number of delivery attempts (max 3)';
COMMENT ON COLUMN notification_logs.sent_at IS 'Timestamp when notification was successfully delivered';

-- =============================================================================
-- Triggers for updated_at
-- =============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_notification_configs_updated_at BEFORE UPDATE ON notification_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_logs_updated_at BEFORE UPDATE ON notification_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- End of Migration
-- =============================================================================

