-- =============================================================================
-- V001 — COMMON MODULE (Foundation)
-- Schemas: tenant, company, user, auth, communication, audit, policy, ai
-- Dependency order: tenant → organization → contact/address → role/department →
--   user → user_department → auth → junctions → audit → policy → event_publication → ai
-- =============================================================================

-- -----------------------------------------------------------------------------
-- SCHEMAS
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS common_tenant;
CREATE SCHEMA IF NOT EXISTS common_company;
CREATE SCHEMA IF NOT EXISTS common_user;
CREATE SCHEMA IF NOT EXISTS common_auth;
CREATE SCHEMA IF NOT EXISTS common_communication;
CREATE SCHEMA IF NOT EXISTS common_audit;
CREATE SCHEMA IF NOT EXISTS common_policy;
CREATE SCHEMA IF NOT EXISTS common_ai;

-- -----------------------------------------------------------------------------
-- TENANT (platform root, no tenant_id)
-- -----------------------------------------------------------------------------
CREATE TABLE common_tenant.common_tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(50) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    billing_email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    trial_ends_at TIMESTAMPTZ,
    subscription_plan VARCHAR(50),
    settings JSONB NOT NULL DEFAULT '{"timezone":"UTC","locale":"en-US","currency":"USD","betaFeaturesEnabled":false,"aiEnabled":true,"emailNotificationsEnabled":true,"mfaRequired":false,"sessionTimeoutMinutes":480}'::jsonb,
    stripe_customer_id VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_uid UNIQUE (uid),
    CONSTRAINT uk_tenant_slug UNIQUE (slug),
    CONSTRAINT chk_tenant_status CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'))
);
CREATE INDEX idx_tenant_status ON common_tenant.common_tenant(status);
CREATE INDEX idx_tenant_active ON common_tenant.common_tenant(is_active) WHERE is_active = TRUE;

-- -----------------------------------------------------------------------------
-- EVENT PUBLICATION (Spring Modulith)
-- -----------------------------------------------------------------------------
CREATE TABLE event_publication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ
);
CREATE INDEX idx_event_publication_date ON event_publication(publication_date);
CREATE INDEX idx_event_completion_date ON event_publication(completion_date);

-- -----------------------------------------------------------------------------
-- ORGANIZATION (company)
-- -----------------------------------------------------------------------------
CREATE TABLE common_company.common_organization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    organization_type VARCHAR(50) NOT NULL DEFAULT 'VERTICAL_MILL',
    parent_organization_id UUID,
    legal_name VARCHAR(255),
    registration_number VARCHAR(100),
    industry VARCHAR(100),
    website VARCHAR(500),
    description TEXT,
    address VARCHAR(500),
    city VARCHAR(100),
    country VARCHAR(100),
    phone_number VARCHAR(50),
    email VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_organization_uid UNIQUE (uid),
    CONSTRAINT uk_organization_tenant_tax_id UNIQUE (tenant_id, tax_id),
    CONSTRAINT fk_organization_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_organization_parent FOREIGN KEY (parent_organization_id) REFERENCES common_company.common_organization(id) ON DELETE SET NULL
);
CREATE INDEX idx_organization_tenant ON common_company.common_organization(tenant_id);
CREATE INDEX idx_organization_type ON common_company.common_organization(organization_type);
CREATE INDEX idx_organization_active ON common_company.common_organization(is_active) WHERE is_active = TRUE;

-- -----------------------------------------------------------------------------
-- OS DEFINITION & SUBSCRIPTION
-- -----------------------------------------------------------------------------
CREATE TABLE common_company.common_os_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    os_type VARCHAR(20) NOT NULL DEFAULT 'FULL',
    description TEXT,
    included_modules JSONB NOT NULL DEFAULT '[]'::jsonb,
    available_tiers JSONB DEFAULT '[]'::jsonb,
    default_tier VARCHAR(50) DEFAULT 'Professional',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_os_definition_uid UNIQUE (uid),
    CONSTRAINT uk_os_definition_os_code UNIQUE (os_code)
);
CREATE INDEX idx_os_definition_os_code ON common_company.common_os_definition(os_code);

CREATE TABLE common_company.common_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    pricing_tier VARCHAR(50),
    start_date TIMESTAMPTZ NOT NULL,
    expiry_date TIMESTAMPTZ,
    trial_ends_at TIMESTAMPTZ,
    features JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_subscription_uid UNIQUE (uid),
    CONSTRAINT uk_subscription_tenant_os UNIQUE (tenant_id, os_code),
    CONSTRAINT fk_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT
);
CREATE INDEX idx_subscription_tenant_os ON common_company.common_subscription(tenant_id, os_code);
CREATE INDEX idx_subscription_status ON common_company.common_subscription(status);

CREATE TABLE common_company.common_feature_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    feature_id VARCHAR(100) NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    available_in_tiers JSONB NOT NULL DEFAULT '["Enterprise"]'::jsonb,
    requires_os VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_feature_catalog_uid UNIQUE (uid),
    CONSTRAINT uk_feature_catalog_feature_id UNIQUE (feature_id),
    CONSTRAINT fk_feature_catalog_os FOREIGN KEY (os_code) REFERENCES common_company.common_os_definition(os_code) ON DELETE CASCADE
);
CREATE INDEX idx_feature_catalog_os_code ON common_company.common_feature_catalog(os_code);

CREATE TABLE common_company.common_subscription_quota (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    subscription_id UUID NOT NULL,
    quota_type VARCHAR(50) NOT NULL,
    quota_limit BIGINT NOT NULL,
    quota_used BIGINT NOT NULL DEFAULT 0,
    reset_period VARCHAR(20) NOT NULL DEFAULT 'NONE',
    last_reset_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_subscription_quota_tenant_sub_type UNIQUE (tenant_id, subscription_id, quota_type),
    CONSTRAINT fk_subscription_quota_subscription FOREIGN KEY (subscription_id) REFERENCES common_company.common_subscription(id) ON DELETE CASCADE
);
CREATE INDEX idx_subscription_quota_subscription ON common_company.common_subscription_quota(subscription_id);

-- -----------------------------------------------------------------------------
-- CONTACT & ADDRESS (communication)
-- -----------------------------------------------------------------------------
CREATE TABLE common_communication.common_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    contact_type VARCHAR(50) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(100),
    parent_contact_id UUID,
    is_personal BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_contact_uid UNIQUE (uid),
    CONSTRAINT uk_contact_tenant_value_type UNIQUE (tenant_id, contact_value, contact_type),
    CONSTRAINT fk_contact_parent FOREIGN KEY (parent_contact_id) REFERENCES common_communication.common_contact(id) ON DELETE SET NULL,
    CONSTRAINT chk_contact_type CHECK (contact_type IN ('EMAIL', 'MOBILE', 'LANDLINE', 'PHONE_EXTENSION', 'FAX', 'WEBSITE', 'SOCIAL_MEDIA'))
);
CREATE INDEX idx_contact_tenant ON common_communication.common_contact(tenant_id);
CREATE INDEX idx_contact_value ON common_communication.common_contact(contact_value);

CREATE TABLE common_communication.common_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
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
    address_line2 VARCHAR(255),
    contact_phone VARCHAR(50),
    contact_email VARCHAR(255),
    contact_person VARCHAR(100),
    address_type VARCHAR(50) NOT NULL,
    label VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_address_uid UNIQUE (uid),
    CONSTRAINT chk_address_type CHECK (address_type IN (
        'HOME', 'BILLING', 'MAILING', 'TEMPORARY', 'ALTERNATE',
        'OFFICE', 'HEADQUARTERS', 'BRANCH', 'WAREHOUSE', 'FACTORY', 'SHIPPING',
        'WORKSITE', 'REMOTE'
    ))
);
CREATE INDEX idx_address_tenant ON common_communication.common_address(tenant_id);
CREATE INDEX idx_address_country_code ON common_communication.common_address(country_code);
CREATE INDEX idx_address_type ON common_communication.common_address(address_type);

-- -----------------------------------------------------------------------------
-- ROLE & DEPARTMENT
-- -----------------------------------------------------------------------------
CREATE TABLE common_user.common_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    role_scope VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_uid UNIQUE (uid),
    CONSTRAINT uk_role_tenant_code UNIQUE (tenant_id, role_code)
);
CREATE INDEX idx_role_tenant ON common_user.common_role(tenant_id);
CREATE INDEX idx_role_scope ON common_user.common_role(role_scope);

CREATE TABLE common_company.common_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    organization_id UUID NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    manager_id UUID,
    parent_department_id UUID,
    is_system_department BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_department_uid UNIQUE (uid),
    CONSTRAINT uk_department_tenant_code UNIQUE (tenant_id, department_code),
    CONSTRAINT fk_department_organization FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_department_id) REFERENCES common_company.common_department(id) ON DELETE SET NULL
);
CREATE INDEX idx_department_tenant ON common_company.common_department(tenant_id);
CREATE INDEX idx_department_organization ON common_company.common_department(organization_id);

-- -----------------------------------------------------------------------------
-- USER & USER_DEPARTMENT
-- -----------------------------------------------------------------------------
CREATE TABLE common_user.common_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    organization_id UUID NOT NULL,
    role_id UUID,
    user_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    last_active_at TIMESTAMPTZ,
    onboarding_completed_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_uid UNIQUE (uid),
    CONSTRAINT fk_user_organization FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES common_user.common_role(id) ON DELETE SET NULL
);
CREATE INDEX idx_user_tenant ON common_user.common_user(tenant_id);
CREATE INDEX idx_user_organization ON common_user.common_user(organization_id);
CREATE INDEX idx_user_role ON common_user.common_user(role_id);
CREATE INDEX idx_user_type ON common_user.common_user(user_type);

CREATE TABLE common_user.common_user_department (
    user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_user_department_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_department_department FOREIGN KEY (department_id) REFERENCES common_company.common_department(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_department_user ON common_user.common_user_department(user_id);
CREATE INDEX idx_user_department_department ON common_user.common_user_department(department_id);
CREATE INDEX idx_user_department_tenant ON common_user.common_user_department(tenant_id);

-- -----------------------------------------------------------------------------
-- AUTH (auth_user, refresh_token, verification_code, registration_token)
-- -----------------------------------------------------------------------------
CREATE TABLE common_auth.common_auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    is_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    primary_mfa_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    mfa_secret VARCHAR(64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_user_uid UNIQUE (uid),
    CONSTRAINT uk_auth_user_user_id UNIQUE (user_id),
    CONSTRAINT fk_auth_user_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_auth_user_user_id ON common_auth.common_auth_user(user_id);

CREATE TABLE common_auth.common_refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    token VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    device_name VARCHAR(255),
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_token_uid UNIQUE (uid),
    CONSTRAINT uk_refresh_token_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_token_user ON common_auth.common_refresh_token(user_id);

CREATE TABLE common_auth.common_verification_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_verification_code_uid UNIQUE (uid)
);
CREATE INDEX idx_verification_code_contact_type ON common_auth.common_verification_code(tenant_id, contact_value, type);

CREATE TABLE common_auth.common_registration_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    token VARCHAR(36) NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    token_type VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMPTZ,
    user_id UUID,
    organization_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_registration_token_uid UNIQUE (uid),
    CONSTRAINT uk_registration_token_token UNIQUE (token),
    CONSTRAINT chk_registration_token_type CHECK (token_type IN ('SALES_LED', 'SELF_SERVICE'))
);
CREATE INDEX idx_registration_token_contact ON common_auth.common_registration_token(contact_value);

-- -----------------------------------------------------------------------------
-- JUNCTIONS: organization_contact, organization_address
-- -----------------------------------------------------------------------------
CREATE TABLE common_company.common_organization_contact (
    organization_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    department VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, contact_id),
    CONSTRAINT uk_organization_contact_uid UNIQUE (uid),
    CONSTRAINT fk_organization_contact_org FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_contact_contact FOREIGN KEY (contact_id) REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);
CREATE INDEX idx_organization_contact_org ON common_company.common_organization_contact(organization_id);
CREATE INDEX idx_organization_contact_tenant ON common_company.common_organization_contact(tenant_id);
CREATE INDEX idx_organization_contact_department ON common_company.common_organization_contact(department);

CREATE TABLE common_company.common_organization_address (
    organization_id UUID NOT NULL,
    address_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_headquarters BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, address_id),
    CONSTRAINT uk_organization_address_uid UNIQUE (uid),
    CONSTRAINT uk_organization_address_address_id UNIQUE (address_id),
    CONSTRAINT fk_organization_address_org FOREIGN KEY (organization_id) REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_address_address FOREIGN KEY (address_id) REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);
CREATE INDEX idx_organization_address_org ON common_company.common_organization_address(organization_id);
CREATE INDEX idx_organization_address_tenant ON common_company.common_organization_address(tenant_id);

-- -----------------------------------------------------------------------------
-- JUNCTIONS: user_contact, user_address, user_work_location
-- -----------------------------------------------------------------------------
CREATE TABLE common_user.common_user_contact (
    user_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, contact_id),
    CONSTRAINT uk_user_contact_uid UNIQUE (uid),
    CONSTRAINT fk_user_contact_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_contact_contact FOREIGN KEY (contact_id) REFERENCES common_communication.common_contact(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_contact_user ON common_user.common_user_contact(user_id);
CREATE INDEX idx_user_contact_tenant ON common_user.common_user_contact(tenant_id);

CREATE TABLE common_user.common_user_address (
    user_id UUID NOT NULL,
    address_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_work_address BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, address_id),
    CONSTRAINT uk_user_address_uid UNIQUE (uid),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_address_address FOREIGN KEY (address_id) REFERENCES common_communication.common_address(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_address_user ON common_user.common_user_address(user_id);
CREATE INDEX idx_user_address_tenant ON common_user.common_user_address(tenant_id);

CREATE TABLE common_user.common_user_work_location (
    user_id UUID NOT NULL,
    org_address_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, org_address_id),
    CONSTRAINT fk_user_work_location_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_work_location_org_address FOREIGN KEY (org_address_id) REFERENCES common_company.common_organization_address(address_id) ON DELETE CASCADE
);
CREATE INDEX idx_user_work_location_user ON common_user.common_user_work_location(user_id);
CREATE INDEX idx_user_work_location_org_address ON common_user.common_user_work_location(org_address_id);
CREATE INDEX idx_user_work_location_tenant ON common_user.common_user_work_location(tenant_id);

-- -----------------------------------------------------------------------------
-- AUDIT LOG
-- -----------------------------------------------------------------------------
CREATE TABLE common_audit.common_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
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
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_audit_log_uid UNIQUE (uid)
);
CREATE INDEX idx_audit_log_tenant_timestamp ON common_audit.common_audit_log(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_log_user ON common_audit.common_audit_log(user_id);
CREATE INDEX idx_audit_log_resource ON common_audit.common_audit_log(resource);

-- -----------------------------------------------------------------------------
-- POLICY
-- -----------------------------------------------------------------------------
CREATE TABLE common_policy.common_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    policy_id VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    effect VARCHAR(10) NOT NULL DEFAULT 'DENY',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    conditions JSONB DEFAULT '{}'::jsonb,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_policy_uid UNIQUE (uid),
    CONSTRAINT uk_policy_policy_id UNIQUE (policy_id)
);
CREATE INDEX idx_policy_resource ON common_policy.common_policy(resource);
CREATE INDEX idx_policy_priority ON common_policy.common_policy(priority DESC);

-- -----------------------------------------------------------------------------
-- AI LOG (non-BaseEntity, app-level logging)
-- -----------------------------------------------------------------------------
CREATE TABLE common_ai.common_ai_log (
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);
CREATE INDEX idx_ai_log_tenant ON common_ai.common_ai_log(tenant_id);
CREATE INDEX idx_ai_log_created ON common_ai.common_ai_log(created_at);
