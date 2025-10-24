-- =============================================================================
-- Migration V1: CREATE AUTH SERVICE TABLES (BaseEntity Compatible)
-- =============================================================================

-- =============================================================================
-- USERS TABLE (Auth-specific user data)
-- =============================================================================
CREATE TABLE IF NOT EXISTS auth_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_value VARCHAR(100) UNIQUE NOT NULL,
    contact_type VARCHAR(20) NOT NULL CHECK (contact_type IN ('EMAIL', 'PHONE')),
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_locked BOOLEAN NOT NULL DEFAULT false,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    last_failed_login TIMESTAMPTZ,
    last_successful_login TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false,
    tenant_id UUID NOT NULL,
    
    CONSTRAINT auth_users_contact_value_length CHECK (char_length(contact_value) >= 3),
    CONSTRAINT auth_users_email_format CHECK (contact_type = 'EMAIL' AND contact_value ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$' OR contact_type = 'PHONE'),
    CONSTRAINT auth_users_failed_attempts_check CHECK (failed_login_attempts >= 0)
);

-- =============================================================================
-- USER ROLES TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS auth_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role_name VARCHAR(50) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by UUID REFERENCES auth_users(id),
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false,
    
    CONSTRAINT auth_user_roles_unique_user_role UNIQUE(user_id, role_name, tenant_id)
);

-- =============================================================================
-- USER PERMISSIONS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS auth_user_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by UUID REFERENCES auth_users(id),
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false,
    
    CONSTRAINT auth_user_permissions_unique_user_permission UNIQUE(user_id, permission_name, resource_type, resource_id, tenant_id)
);

-- =============================================================================
-- REFRESH TOKENS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    is_revoked BOOLEAN NOT NULL DEFAULT false,
    device_info JSONB,
    ip_address INET,
    tenant_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT false,
    
    CONSTRAINT auth_refresh_tokens_expires_future CHECK (expires_at > created_at)
);

-- =============================================================================
-- LOGIN SESSIONS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS auth_login_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_activity TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    device_info JSONB,
    ip_address INET,
    user_agent TEXT,
    tenant_id UUID NOT NULL,
    
    CONSTRAINT auth_login_sessions_expires_future CHECK (expires_at > started_at)
);

-- =============================================================================
-- AUDIT LOG TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS auth_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth_users(id),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    details JSONB,
    ip_address INET,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id UUID NOT NULL
);

-- =============================================================================
-- INDEXES FOR PERFORMANCE
-- =============================================================================

-- Auth Users indexes
CREATE INDEX IF NOT EXISTS idx_auth_users_contact_value ON auth_users(contact_value);
CREATE INDEX IF NOT EXISTS idx_auth_users_contact_type ON auth_users(contact_type);
CREATE INDEX IF NOT EXISTS idx_auth_users_tenant ON auth_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_users_active ON auth_users(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_auth_users_deleted ON auth_users(deleted) WHERE deleted = false;

-- User Roles indexes
CREATE INDEX IF NOT EXISTS idx_auth_user_roles_user_id ON auth_user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_roles_role ON auth_user_roles(role_name);
CREATE INDEX IF NOT EXISTS idx_auth_user_roles_tenant ON auth_user_roles(tenant_id);

-- User Permissions indexes
CREATE INDEX IF NOT EXISTS idx_auth_user_permissions_user_id ON auth_user_permissions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_permissions_permission ON auth_user_permissions(permission_name);
CREATE INDEX IF NOT EXISTS idx_auth_user_permissions_resource ON auth_user_permissions(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_permissions_tenant ON auth_user_permissions(tenant_id);

-- Refresh Tokens indexes
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_user_id ON auth_refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_expires ON auth_refresh_tokens(expires_at) WHERE is_revoked = false;
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_tenant ON auth_refresh_tokens(tenant_id);

-- Login Sessions indexes
CREATE INDEX IF NOT EXISTS idx_auth_login_sessions_user_id ON auth_login_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_login_sessions_token ON auth_login_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_auth_login_sessions_expires ON auth_login_sessions(expires_at) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_auth_login_sessions_tenant ON auth_login_sessions(tenant_id);

-- Audit Log indexes
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_user_id ON auth_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_action ON auth_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_created_at ON auth_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_tenant ON auth_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_success ON auth_audit_log(success);

-- =============================================================================
-- COMMENTS FOR DOCUMENTATION
-- =============================================================================
COMMENT ON TABLE auth_users IS 'Authentication-specific user data (separate from user-service)';
COMMENT ON TABLE auth_user_roles IS 'User role assignments for authorization';
COMMENT ON TABLE auth_user_permissions IS 'Granular user permissions';
COMMENT ON TABLE auth_refresh_tokens IS 'JWT refresh tokens for session management';
COMMENT ON TABLE auth_login_sessions IS 'Active login sessions';
COMMENT ON TABLE auth_audit_log IS 'Security audit trail';
