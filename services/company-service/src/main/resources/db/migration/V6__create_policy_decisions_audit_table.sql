-- ============================================================================
-- Migration V6: Create Policy Decisions Audit Table
-- Feature: Policy Authorization System
-- Description: Immutable audit log for all policy decisions
-- 
-- Purpose:
-- - Compliance (WHO accessed WHAT, WHEN, WHY)
-- - Security investigation
-- - Performance monitoring
-- 
-- Performance: Will grow fast - consider partitioning after 1M rows
-- ============================================================================

CREATE TABLE IF NOT EXISTS policy_decisions_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    company_id UUID,
    company_type VARCHAR(50),
    endpoint VARCHAR(200) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    scope VARCHAR(50),
    decision VARCHAR(10) NOT NULL,
    reason TEXT NOT NULL,
    policy_version VARCHAR(20),
    request_ip VARCHAR(50),
    request_id VARCHAR(100),
    correlation_id VARCHAR(100),
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_audit_decision CHECK (decision IN ('ALLOW', 'DENY'))
);

COMMENT ON TABLE policy_decisions_audit IS 'Immutable policy decision audit log (V6)';

-- Indexes (IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_audit_user_decision ON policy_decisions_audit(user_id, decision);
CREATE INDEX IF NOT EXISTS idx_audit_endpoint ON policy_decisions_audit(endpoint);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON policy_decisions_audit(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_correlation ON policy_decisions_audit(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_audit_deny_events ON policy_decisions_audit(user_id, endpoint, created_at DESC) WHERE decision = 'DENY';

