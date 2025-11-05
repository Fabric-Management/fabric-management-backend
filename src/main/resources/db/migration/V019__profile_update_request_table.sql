-- ============================================================================
-- V19: Profile Update Request Table
-- ============================================================================
-- Creates table for user profile update requests requiring HR/Admin approval
-- Last Updated: 2025-01-27
-- ============================================================================

-- ============================================================================
-- TABLE: profile_update_request
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.profile_update_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    user_id UUID NOT NULL,
    profile_category VARCHAR(50) NOT NULL,  -- WORK_PROFILE or PERSONAL_PROFILE
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    
    requested_changes JSONB,  -- Requested changes in JSON format
    reason TEXT,  -- Reason provided by user
    
    reviewed_by UUID,  -- ID of HR/Admin who reviewed
    review_comment TEXT,  -- Review comments
    reviewed_at TIMESTAMP,  -- When request was reviewed
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_profile_req_user FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_req_reviewer FOREIGN KEY (reviewed_by) 
        REFERENCES common_user.common_user(id) ON DELETE SET NULL
);

-- Indexes for performance
CREATE INDEX idx_profile_req_user ON common_user.profile_update_request(tenant_id, user_id);
CREATE INDEX idx_profile_req_status ON common_user.profile_update_request(tenant_id, status);
CREATE INDEX idx_profile_req_created ON common_user.profile_update_request(tenant_id, created_at);
CREATE INDEX idx_profile_req_pending ON common_user.profile_update_request(tenant_id, status) 
    WHERE status = 'PENDING';

COMMENT ON TABLE common_user.profile_update_request IS 'User profile update requests requiring HR/Admin approval';
COMMENT ON COLUMN common_user.profile_update_request.profile_category IS 'Category: WORK_PROFILE or PERSONAL_PROFILE';
COMMENT ON COLUMN common_user.profile_update_request.status IS 'Status: PENDING, APPROVED, REJECTED';
COMMENT ON COLUMN common_user.profile_update_request.requested_changes IS 'Requested changes in JSON format (e.g., {"firstName": "John", "personalPhone": "+1234567890"})';
COMMENT ON COLUMN common_user.profile_update_request.reason IS 'Reason provided by user for the request';
COMMENT ON COLUMN common_user.profile_update_request.reviewed_by IS 'ID of HR/Admin who reviewed the request';
COMMENT ON COLUMN common_user.profile_update_request.review_comment IS 'Review comments from HR/Admin';

