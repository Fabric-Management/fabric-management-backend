-- ============================================================================
-- V1: Common Schemas - Foundation Layer
-- ============================================================================
-- Creates all schemas and sequences for common platform modules
-- Last Updated: 2025-10-25
-- ============================================================================

-- Create schemas
CREATE SCHEMA IF NOT EXISTS common_company;
CREATE SCHEMA IF NOT EXISTS common_user;
CREATE SCHEMA IF NOT EXISTS common_auth;
CREATE SCHEMA IF NOT EXISTS common_policy;
CREATE SCHEMA IF NOT EXISTS common_audit;

-- Create sequence for UID generation
-- Pattern: {TENANT_UID}-{MODULE}-{SEQUENCE}
CREATE SEQUENCE IF NOT EXISTS common_company.seq_company START 1000;
CREATE SEQUENCE IF NOT EXISTS common_company.seq_department START 1000;
CREATE SEQUENCE IF NOT EXISTS common_company.seq_subscription START 1000;
CREATE SEQUENCE IF NOT EXISTS common_user.seq_user START 1000;
CREATE SEQUENCE IF NOT EXISTS common_auth.seq_verification_code START 1000;
CREATE SEQUENCE IF NOT EXISTS common_policy.seq_policy START 1000;
CREATE SEQUENCE IF NOT EXISTS common_audit.seq_audit_log START 1000;

COMMENT ON SCHEMA common_company IS 'Company/Tenant management, subscriptions, OS definitions';
COMMENT ON SCHEMA common_user IS 'User management';
COMMENT ON SCHEMA common_auth IS 'Authentication, verification codes, refresh tokens';
COMMENT ON SCHEMA common_policy IS 'Policy definitions, access control rules';
COMMENT ON SCHEMA common_audit IS 'Audit trail, compliance logging';

-- ============================================================================
-- TABLE: event_publication (Spring Modulith Events)
-- ============================================================================
-- This table is used by Spring Modulith for reliable event publishing
-- Events are stored here and processed asynchronously
CREATE TABLE IF NOT EXISTS event_publication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP(6) NOT NULL,
    completion_date TIMESTAMP(6)
);

CREATE INDEX idx_event_publication_date ON event_publication(publication_date);
CREATE INDEX idx_event_completion_date ON event_publication(completion_date);
CREATE INDEX idx_event_serialized_event_hash ON event_publication(MD5(serialized_event));

COMMENT ON TABLE event_publication IS 'Spring Modulith event publication log for reliable async events';

