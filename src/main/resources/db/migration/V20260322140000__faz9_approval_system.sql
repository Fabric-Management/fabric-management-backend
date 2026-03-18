-- =========================================================================
-- FAZ 9: ONAY SİSTEMİ (APPROVAL SYSTEM)
-- =========================================================================

-- =========================================================================
-- 1. SCHEMA DEFINITION (MUST come before enums)
-- =========================================================================
CREATE SCHEMA IF NOT EXISTS common_approval;
CREATE SCHEMA IF NOT EXISTS common_auth;

-- =========================================================================
-- 2. ENUMS
-- =========================================================================
CREATE TYPE common_approval.policy_target_level AS ENUM (
    'ALL',
    'PROBATION',
    'STANDARD'
);

CREATE TYPE common_approval.approval_request_status AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED',
    'CANCELLED'
);

CREATE TYPE common_approval.promotion_request_status AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED'
);

CREATE TYPE common_approval.promotion_trigger_type AS ENUM (
    'SYSTEM',
    'MANUAL'
);

CREATE TYPE common_auth.user_trust_level AS ENUM (
    'PROBATION',
    'STANDARD',
    'TRUSTED'
);

-- =========================================================================
-- 3. CORE TABLES
-- =========================================================================

-- 3.A. APPROVAL_POLICY
CREATE TABLE IF NOT EXISTS common_approval.approval_policy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    required_for_level common_approval.policy_target_level NOT NULL,
    approver_role VARCHAR(50) NOT NULL,
    promotion_threshold INT NOT NULL DEFAULT 10,
    is_active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_approval_policy_entity_target UNIQUE (tenant_id, entity_type, required_for_level)
);

CREATE INDEX IF NOT EXISTS idx_approval_policy_active ON common_approval.approval_policy(tenant_id, entity_type, required_for_level) WHERE is_active = true AND deleted_at IS NULL;

-- 3.B. APPROVAL_REQUEST
CREATE TABLE IF NOT EXISTS common_approval.approval_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    
    -- Polymorphic relation
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    
    policy_id UUID NOT NULL REFERENCES common_approval.approval_policy(id),
    requested_by UUID NOT NULL,
    approver_id UUID,

    status common_approval.approval_request_status NOT NULL DEFAULT 'PENDING',
    approved_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0
);

-- Hızlı sorgulama için PENDING durumdaki bekleyenler indexi
CREATE INDEX IF NOT EXISTS idx_approval_req_status ON common_approval.approval_request(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_approval_req_entity ON common_approval.approval_request(tenant_id, entity_type, entity_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_approval_req_user ON common_approval.approval_request(tenant_id, requested_by) WHERE deleted_at IS NULL;

-- 3.C. USER_PROMOTION_REQUEST
CREATE TABLE IF NOT EXISTS common_approval.user_promotion_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,

    from_level common_auth.user_trust_level NOT NULL,
    to_level common_auth.user_trust_level NOT NULL,
    
    status common_approval.promotion_request_status NOT NULL DEFAULT 'PENDING',
    triggered_by common_approval.promotion_trigger_type NOT NULL,
    
    reviewed_by UUID,
    admin_note TEXT,
    rejection_count INT NOT NULL DEFAULT 0,
    approved_transaction_count INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_promotion_req_user ON common_approval.user_promotion_request(tenant_id, user_id, status) WHERE deleted_at IS NULL;

-- =========================================================================
-- 4. ALTER EXISTING USER TABLE (common_auth)
-- =========================================================================
ALTER TABLE common_auth.users ADD COLUMN trust_level common_auth.user_trust_level DEFAULT 'PROBATION';

-- Mevcut kullanıcıların tümü şimdilik TRUSTED olarak başlatılabilir, ilerleyen dönem girişleri PROBATION olur.
UPDATE common_auth.users SET trust_level = 'TRUSTED' WHERE trust_level IS NULL;
ALTER TABLE common_auth.users ALTER COLUMN trust_level SET NOT NULL;
