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

