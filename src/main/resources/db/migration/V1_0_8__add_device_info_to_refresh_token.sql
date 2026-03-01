-- V1_0_8: Add device tracking columns to refresh token for session management
-- Enables "Active Sessions" listing, per-device logout, and session audit trail

ALTER TABLE common_auth.common_refresh_token
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

ALTER TABLE common_auth.common_refresh_token
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(1000);

ALTER TABLE common_auth.common_refresh_token
    ADD COLUMN IF NOT EXISTS device_name VARCHAR(255);
