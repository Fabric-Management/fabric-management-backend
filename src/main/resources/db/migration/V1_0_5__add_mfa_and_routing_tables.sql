-- Add MFA fields to common_auth_user
ALTER TABLE common_auth.common_auth_user 
ADD COLUMN IF NOT EXISTS is_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS primary_mfa_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(64);

-- Create Verification (Notification) Log for Outbox & Fallback Pattern
CREATE TABLE common_communication.common_verification_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    user_id UUID NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    verification_type VARCHAR(50) NOT NULL,
    delivery_channel VARCHAR(30) NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255),
    error_message TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vl_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id),
    CONSTRAINT fk_vl_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id)
);

CREATE UNIQUE INDEX idx_vl_uid ON common_communication.common_verification_log(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_vl_tenant ON common_communication.common_verification_log(tenant_id);
CREATE INDEX idx_vl_status ON common_communication.common_verification_log(delivery_status);
CREATE INDEX idx_vl_created_at ON common_communication.common_verification_log(created_at);

-- Create Routing Config for Market-Based Channel Selection
CREATE TABLE common_communication.common_routing_config (
    id UUID PRIMARY KEY,
    tenant_id UUID,          -- Global fallback if null, specific tenant overrides if populated
    uid VARCHAR(100),
    country_code VARCHAR(10), -- e.g., 'TR', 'UK', 'US'
    primary_channel VARCHAR(30) NOT NULL,
    fallback_channel VARCHAR(30),
    timeout_seconds INTEGER NOT NULL DEFAULT 20,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_rc_tenant_country UNIQUE (tenant_id, country_code)
);

CREATE UNIQUE INDEX idx_rc_uid ON common_communication.common_routing_config(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_rc_tenant ON common_communication.common_routing_config(tenant_id);

-- Insert Default Market Routings (Global configurations where tenant_id is null)
-- 'TR' (Turkey) -> WhatsApp Primary, SMS Fallback
INSERT INTO common_communication.common_routing_config 
(id, uid, country_code, primary_channel, fallback_channel, timeout_seconds, created_at, updated_at) 
VALUES 
(gen_random_uuid(), 'ROUTE-' || gen_random_uuid(), 'TR', 'WHATSAPP', 'SMS', 15, current_timestamp, current_timestamp);

-- 'GB' (UK) -> Email Primary, None Fallback
INSERT INTO common_communication.common_routing_config 
(id, uid, country_code, primary_channel, fallback_channel, timeout_seconds, created_at, updated_at) 
VALUES 
(gen_random_uuid(), 'ROUTE-' || gen_random_uuid(), 'GB', 'EMAIL', NULL, 0, current_timestamp, current_timestamp);

