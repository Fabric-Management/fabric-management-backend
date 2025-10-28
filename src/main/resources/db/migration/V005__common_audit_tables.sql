-- ============================================================================
-- V6: Audit Module Tables
-- ============================================================================
-- Comprehensive audit trail for compliance (GDPR, ISO 27001, SOC 2)
-- Last Updated: 2025-10-25
-- ============================================================================

-- ============================================================================
-- TABLE: common_audit_log
-- ============================================================================
CREATE TABLE common_audit.common_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    user_id UUID,
    user_uid VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    
    description TEXT,
    old_value TEXT,
    new_value TEXT,
    
    ip_address VARCHAR(50),
    user_agent TEXT,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_user ON common_audit.common_audit_log(user_id);
CREATE INDEX idx_audit_resource ON common_audit.common_audit_log(resource);
CREATE INDEX idx_audit_action ON common_audit.common_audit_log(action);
CREATE INDEX idx_audit_timestamp ON common_audit.common_audit_log(timestamp DESC);
CREATE INDEX idx_audit_severity ON common_audit.common_audit_log(severity);
CREATE INDEX idx_audit_tenant_time ON common_audit.common_audit_log(tenant_id, timestamp DESC);

COMMENT ON TABLE common_audit.common_audit_log IS 'Comprehensive audit trail - all critical operations logged';
COMMENT ON COLUMN common_audit.common_audit_log.severity IS 'INFO, WARNING, ERROR, CRITICAL';
COMMENT ON COLUMN common_audit.common_audit_log.old_value IS 'JSON before change (for UPDATE operations)';
COMMENT ON COLUMN common_audit.common_audit_log.new_value IS 'JSON after change (for UPDATE operations)';

-- Create partition for performance (optional, for high-volume systems)
-- Partition by month for audit logs older than 3 months
COMMENT ON TABLE common_audit.common_audit_log IS 'Consider partitioning by timestamp for high-volume systems';

