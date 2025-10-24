-- ============================================================================
-- Migration V9: Add http_method to policy_decisions_audit
-- Feature: Policy Authorization System Enhancement
-- Description: Track HTTP method for better audit trail
-- ============================================================================

ALTER TABLE policy_decisions_audit 
ADD COLUMN IF NOT EXISTS http_method VARCHAR(10);

COMMENT ON COLUMN policy_decisions_audit.http_method IS 'HTTP method (GET, POST, PUT, DELETE, etc.)';

-- Create index for method-based queries
CREATE INDEX IF NOT EXISTS idx_audit_http_method 
ON policy_decisions_audit(http_method) 
WHERE http_method IS NOT NULL;

