-- ============================================
-- MODULE: COMMON (Foundation)
-- Birleştirilen migration'lar: V001, V002, V003, V005, V006, V007, V013, V015, V016, V034, V035, V036, V037, V038, V045, V046, V047, V050, V051, V052, V053, V054, V067, V072
-- Yol haritası sırası: tenant → organization → user → auth → contact/address → junctions → role/department/user_department → audit → policy → ai
-- ============================================

-- ============================================================================
-- SCHEMAS
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS common_company;
CREATE SCHEMA IF NOT EXISTS common_user;
CREATE SCHEMA IF NOT EXISTS common_auth;
CREATE SCHEMA IF NOT EXISTS common_policy;
CREATE SCHEMA IF NOT EXISTS common_audit;
CREATE SCHEMA IF NOT EXISTS common_communication;
CREATE SCHEMA IF NOT EXISTS common_tenant;

COMMENT ON SCHEMA common_company IS 'Company/Organization, subscriptions, OS definitions';
COMMENT ON SCHEMA common_user IS 'User management';
COMMENT ON SCHEMA common_auth IS 'Authentication, verification, refresh tokens';
COMMENT ON SCHEMA common_policy IS 'Policy definitions';
COMMENT ON SCHEMA common_audit IS 'Audit trail';
COMMENT ON SCHEMA common_communication IS 'Contacts, addresses, notifications';
COMMENT ON SCHEMA common_tenant IS 'Platform-level tenant (subscription boundary)';

-- ============================================================================
-- SEQUENCES
-- ============================================================================
CREATE SEQUENCE IF NOT EXISTS common_company.seq_company START 1000;
CREATE SEQUENCE IF NOT EXISTS common_company.seq_department START 1000;
CREATE SEQUENCE IF NOT EXISTS common_company.seq_subscription START 1000;
CREATE SEQUENCE IF NOT EXISTS common_user.seq_user START 1000;
CREATE SEQUENCE IF NOT EXISTS common_auth.seq_verification_code START 1000;
CREATE SEQUENCE IF NOT EXISTS common_policy.seq_policy START 1000;
CREATE SEQUENCE IF NOT EXISTS common_audit.seq_audit_log START 1000;
CREATE SEQUENCE IF NOT EXISTS common_communication.seq_contact START 1000;
CREATE SEQUENCE IF NOT EXISTS common_communication.seq_address START 1000;

-- ============================================================================
-- TABLE: common_tenant (FK bağımlılığı — organization, user, auth, notification önce buna referans verir)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_tenant.common_tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(50) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    billing_email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    trial_ends_at TIMESTAMP,
    subscription_plan VARCHAR(50),
    settings JSONB NOT NULL DEFAULT '{"timezone":"UTC","locale":"en-US","currency":"USD","betaFeaturesEnabled":false,"aiEnabled":true,"emailNotificationsEnabled":true,"mfaRequired":false,"sessionTimeoutMinutes":480}'::jsonb,
    stripe_customer_id VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_uid UNIQUE (uid),
    CONSTRAINT uk_tenant_slug UNIQUE (slug),
    CONSTRAINT chk_tenant_status CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'))
);

CREATE INDEX idx_tenant_status ON common_tenant.common_tenant(status);
CREATE INDEX idx_tenant_active ON common_tenant.common_tenant(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_tenant_trial_expiry ON common_tenant.common_tenant(status, trial_ends_at) WHERE status = 'TRIAL' AND trial_ends_at IS NOT NULL;

-- ============================================================================
-- TABLE: event_publication (Spring Modulith)
-- ============================================================================
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

-- ============================================================================
-- 1. common_company → common_organization (V046: name, organization_type, parent_organization_id, tenant_id FK)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_organization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    address VARCHAR(500),
    city VARCHAR(100),
    country VARCHAR(100),
    phone_number VARCHAR(50),
    email VARCHAR(255),
    organization_type VARCHAR(50) NOT NULL DEFAULT 'VERTICAL_MILL',
    parent_organization_id UUID,
    legal_name VARCHAR(255),
    registration_number VARCHAR(100),
    industry VARCHAR(100),
    website VARCHAR(500),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_organization_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_organization_parent FOREIGN KEY (parent_organization_id) REFERENCES common_company.common_organization(id) ON DELETE SET NULL,
    CONSTRAINT uk_organization_tenant_tax_id UNIQUE (tenant_id, tax_id)
);

CREATE INDEX idx_organization_tenant ON common_company.common_organization(tenant_id);
CREATE INDEX idx_organization_type ON common_company.common_organization(organization_type);
CREATE INDEX idx_organization_active ON common_company.common_organization(is_active) WHERE is_active = TRUE;

-- ============================================================================
-- 2. common_os_definition, common_subscription, common_feature_catalog, common_subscription_quota
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_os_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    os_code VARCHAR(50) UNIQUE NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    os_type VARCHAR(20) NOT NULL DEFAULT 'FULL',
    description TEXT,
    included_modules JSONB NOT NULL DEFAULT '[]'::jsonb,
    available_tiers JSONB DEFAULT '[]'::jsonb,
    default_tier VARCHAR(50) DEFAULT 'Professional',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_os_code ON common_company.common_os_definition(os_code);

CREATE TABLE IF NOT EXISTS common_company.common_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    pricing_tier VARCHAR(50),
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    trial_ends_at TIMESTAMP,
    features JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT uk_tenant_os UNIQUE (tenant_id, os_code)
);

CREATE INDEX idx_subscription_tenant_os ON common_company.common_subscription(tenant_id, os_code);
CREATE INDEX idx_subscription_status ON common_company.common_subscription(status);

CREATE TABLE IF NOT EXISTS common_company.common_feature_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    feature_id VARCHAR(100) UNIQUE NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    available_in_tiers JSONB NOT NULL DEFAULT '["Enterprise"]'::jsonb,
    requires_os VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_feature_os FOREIGN KEY (os_code) REFERENCES common_company.common_os_definition(os_code) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS common_company.common_subscription_quota (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    subscription_id UUID NOT NULL,
    quota_type VARCHAR(50) NOT NULL,
    quota_limit BIGINT NOT NULL,
    quota_used BIGINT NOT NULL DEFAULT 0,
    reset_period VARCHAR(20) NOT NULL DEFAULT 'NONE',
    last_reset_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_subscription_quota UNIQUE (tenant_id, subscription_id, quota_type),
    CONSTRAINT fk_quota_subscription FOREIGN KEY (subscription_id) REFERENCES common_company.common_subscription(id) ON DELETE CASCADE
);

-- ============================================================================
-- 3. common_contact, common_address (V053: is_primary junction'lara taşındı; V036: is_whatsapp kaldırıldı, address_type güncel)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_communication.common_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    contact_type VARCHAR(50) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(100),
    parent_contact_id UUID,
    is_personal BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_contact_parent FOREIGN KEY (parent_contact_id) REFERENCES common_communication.common_contact(id) ON DELETE SET NULL,
    CONSTRAINT chk_contact_type CHECK (contact_type IN ('EMAIL', 'MOBILE', 'LANDLINE', 'PHONE_EXTENSION', 'FAX', 'WEBSITE', 'SOCIAL_MEDIA')),
    CONSTRAINT uk_contact_tenant_value_type UNIQUE (tenant_id, contact_value, contact_type)
);

CREATE INDEX idx_contact_tenant ON common_communication.common_contact(tenant_id);
CREATE INDEX idx_contact_value ON common_communication.common_contact(contact_value);

CREATE TABLE IF NOT EXISTS common_communication.common_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    street_address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) NOT NULL,
    country_code VARCHAR(2),
    district VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    place_id VARCHAR(255),
    formatted_address VARCHAR(500),
    address_line2 VARCHAR(500),
    contact_phone VARCHAR(50),
    contact_email VARCHAR(255),
    contact_person VARCHAR(100),
    address_type VARCHAR(50) NOT NULL,
    label VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_address_type CHECK (address_type IN (
        'HOME', 'BILLING', 'MAILING', 'TEMPORARY', 'ALTERNATE',
        'OFFICE', 'HEADQUARTERS', 'BRANCH', 'WAREHOUSE', 'FACTORY', 'SHIPPING',
        'WORKSITE', 'REMOTE'
    ))
);

CREATE INDEX idx_address_tenant ON common_communication.common_address(tenant_id);
CREATE INDEX idx_address_country_code ON common_communication.common_address(country_code);

-- ============================================================================
-- 4. common_role (common_user), common_department (common_company) — V050: department_category yok, role_scope var
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    role_scope VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_tenant_code UNIQUE (tenant_id, role_code)
);

CREATE INDEX idx_role_tenant ON common_user.common_role(tenant_id);
CREATE INDEX idx_role_scope ON common_user.common_role(role_scope);

CREATE TABLE IF NOT EXISTS common_company.common_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    organization_id UUID NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    manager_id UUID,
    parent_department_id UUID REFERENCES common_company.common_department(id),
    is_system_department BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_department_organization FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT uq_department_tenant_code UNIQUE (tenant_id, department_code)
);

CREATE INDEX idx_department_tenant ON common_company.common_department(tenant_id);
CREATE INDEX idx_department_organization ON common_company.common_department(organization_id);

-- ============================================================================
-- 5. common_user (V052: department, contact_value, contact_type, display_name kaldırıldı; organization_id, role_id; V050 user_type)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    organization_id UUID NOT NULL,
    role_id UUID,
    last_active_at TIMESTAMP,
    onboarding_completed_at TIMESTAMP,
    user_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_organization FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES common_user.common_role(id)
);

CREATE INDEX idx_user_tenant_organization ON common_user.common_user(tenant_id, organization_id);
CREATE INDEX idx_user_role ON common_user.common_user(role_id);
CREATE INDEX idx_user_type ON common_user.common_user(user_type);

-- ============================================================================
-- 6. common_user_department
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_user_department (
    user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_user_dept_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_dept_department FOREIGN KEY (department_id) REFERENCES common_company.common_department(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_dept_user ON common_user.common_user_department(user_id);
CREATE INDEX idx_user_dept_department ON common_user.common_user_department(department_id);

-- ============================================================================
-- 7. common_auth (V038: contact_id yok; V049: MFA alanları, refresh_token device alanları)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_auth.common_auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    is_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    primary_mfa_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    mfa_secret VARCHAR(64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_user_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_user_id ON common_auth.common_auth_user(user_id);

CREATE TABLE IF NOT EXISTS common_auth.common_refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    device_name VARCHAR(255),
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_refresh_token ON common_auth.common_refresh_token(token);
CREATE INDEX idx_refresh_user ON common_auth.common_refresh_token(user_id);

CREATE TABLE IF NOT EXISTS common_auth.common_verification_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_verification_contact_type ON common_auth.common_verification_code(tenant_id, contact_value, type);

CREATE TABLE IF NOT EXISTS common_auth.common_registration_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    token VARCHAR(36) UNIQUE NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    token_type VARCHAR(20) NOT NULL CHECK (token_type IN ('SALES_LED', 'SELF_SERVICE')),
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    user_id UUID,
    organization_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_registration_token_contact ON common_auth.common_registration_token(contact_value);

-- ============================================================================
-- 8. Junctions: common_organization_contact, common_organization_address (V046 isimleri)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_organization_contact (
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    organization_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    department VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, contact_id),
    CONSTRAINT fk_org_contact_org FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_contact_contact FOREIGN KEY (contact_id) REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_org_contact_org ON common_company.common_organization_contact(organization_id);
CREATE INDEX idx_org_contact_tenant ON common_company.common_organization_contact(tenant_id);

CREATE TABLE IF NOT EXISTS common_company.common_organization_address (
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    organization_id UUID NOT NULL,
    address_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_headquarters BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, address_id),
    CONSTRAINT uq_org_address_address_id UNIQUE (address_id),
    CONSTRAINT fk_org_address_org FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_address_address FOREIGN KEY (address_id) REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);

CREATE INDEX idx_org_address_org ON common_company.common_organization_address(organization_id);
CREATE INDEX idx_org_address_tenant ON common_company.common_organization_address(tenant_id);

-- ============================================================================
-- 9. common_user_contact, common_user_address, common_user_work_location
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_user_contact (
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, contact_id),
    CONSTRAINT fk_user_contact_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_contact_contact FOREIGN KEY (contact_id) REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_contact_user ON common_user.common_user_contact(user_id);
CREATE INDEX idx_user_contact_tenant ON common_user.common_user_contact(tenant_id);

CREATE TABLE IF NOT EXISTS common_user.common_user_address (
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    address_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_work_address BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, address_id),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_address_address FOREIGN KEY (address_id) REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_address_user ON common_user.common_user_address(user_id);
CREATE INDEX idx_user_address_tenant ON common_user.common_user_address(tenant_id);

CREATE TABLE IF NOT EXISTS common_user.common_user_work_location (
    user_id UUID NOT NULL,
    org_address_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(255),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_work_location PRIMARY KEY (user_id, org_address_id),
    CONSTRAINT fk_uwl_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id),
    CONSTRAINT fk_uwl_org_address FOREIGN KEY (org_address_id) REFERENCES common_company.common_organization_address(address_id)
);

CREATE INDEX idx_uwl_user ON common_user.common_user_work_location(user_id);
CREATE INDEX idx_uwl_org_address ON common_user.common_user_work_location(org_address_id);
CREATE INDEX idx_uwl_tenant ON common_user.common_user_work_location(tenant_id);

-- ============================================================================
-- 10. common_audit_log, common_policy (V051: deleted_at audit_log'da)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_audit.common_audit_log (
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
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_tenant_time ON common_audit.common_audit_log(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_user ON common_audit.common_audit_log(user_id);

CREATE TABLE IF NOT EXISTS common_policy.common_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    policy_id VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    effect VARCHAR(10) NOT NULL DEFAULT 'DENY',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    conditions JSONB DEFAULT '{}'::jsonb,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_policy_resource ON common_policy.common_policy(resource);

-- ============================================================================
-- 11. common_ai (schema + common_ai_log)
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS common_ai;

CREATE TABLE IF NOT EXISTS common_ai.common_ai_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    input_message TEXT NOT NULL,
    output_message TEXT NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms INTEGER,
    finish_reason VARCHAR(50),
    conversation_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

CREATE INDEX idx_ai_log_tenant ON common_ai.common_ai_log(tenant_id);
CREATE INDEX idx_ai_log_created ON common_ai.common_ai_log(created_at);

-- [COMMON] module migration tamamlandı.
-- Tablo sayısı: 28 (tenant, event_publication, organization, os_definition, subscription, feature_catalog, subscription_quota,
--   contact, address, role, department, user, user_department, auth_user, refresh_token, verification_code, registration_token,
--   organization_contact, organization_address, user_contact, user_address, user_work_location, audit_log, policy, ai_log)
-- Toplam index sayısı: 60+
