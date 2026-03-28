-- ============================================
-- MODULE: NOTIFICATION
-- Birleştirilen migration'lar: V026, V049, V079, V083
-- Cross-module: verification_log, routing_config (common_communication); trusted_device (common_auth).
-- INSERT (routing_config TR, GB) → V010__SEEDS.sql
-- ============================================

-- common_communication, common_auth, common_tenant, common_user schemas created in V001 COMMON.

-- =====================================================
-- common_verification_log (V049)
-- =====================================================
CREATE TABLE IF NOT EXISTS common_communication.common_verification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    user_id UUID NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    verification_type VARCHAR(50) NOT NULL,
    delivery_channel VARCHAR(30) NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255),
    error_message TEXT,
    country_code VARCHAR(10),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vl_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id),
    CONSTRAINT fk_vl_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id)
);

CREATE UNIQUE INDEX idx_vl_uid ON common_communication.common_verification_log(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_vl_tenant ON common_communication.common_verification_log(tenant_id);
CREATE INDEX idx_vl_status ON common_communication.common_verification_log(delivery_status);
CREATE INDEX idx_vl_created_at ON common_communication.common_verification_log(created_at);
CREATE INDEX IF NOT EXISTS idx_vl_country_code ON common_communication.common_verification_log(country_code);

-- =====================================================
-- common_routing_config (V049; INSERT → V010)
-- =====================================================
CREATE TABLE IF NOT EXISTS common_communication.common_routing_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    uid VARCHAR(100),
    country_code VARCHAR(10),
    primary_channel VARCHAR(30) NOT NULL,
    fallback_channel VARCHAR(30),
    timeout_seconds INTEGER NOT NULL DEFAULT 20,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_rc_tenant_country UNIQUE (tenant_id, country_code)
);

CREATE UNIQUE INDEX idx_rc_uid ON common_communication.common_routing_config(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_rc_tenant ON common_communication.common_routing_config(tenant_id);

-- =====================================================
-- common_trusted_device (V049; common_auth schema)
-- =====================================================
CREATE TABLE IF NOT EXISTS common_auth.common_trusted_device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    user_id UUID NOT NULL,
    device_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_trusted_device_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_trusted_device_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_trusted_device_uid ON common_auth.common_trusted_device(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_trusted_device_user_id ON common_auth.common_trusted_device(user_id);
CREATE INDEX idx_trusted_device_hash ON common_auth.common_trusted_device(device_hash);

-- =====================================================
-- communication_email_outbox (V026)
-- =====================================================
CREATE TABLE IF NOT EXISTS common_communication.communication_email_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    html_body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    last_error TEXT,
    sent_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_email_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_email_outbox_status ON common_communication.communication_email_outbox(status);
CREATE INDEX idx_email_outbox_created_at ON common_communication.communication_email_outbox(created_at);
CREATE INDEX idx_email_outbox_tenant ON common_communication.communication_email_outbox(tenant_id);
CREATE INDEX idx_email_outbox_next_retry ON common_communication.communication_email_outbox(next_retry_at) WHERE next_retry_at IS NOT NULL;

-- =====================================================
-- common_notification (V079; V083: BATCH_NO_QUALITY_STANDARD added to chk_notif_type)
-- =====================================================
CREATE TABLE IF NOT EXISTS common_communication.common_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    recipient_id UUID,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    reference_id UUID,
    reference_type VARCHAR(50),
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notif_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id),
    CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES common_user.common_user(id) ON DELETE SET NULL,
    CONSTRAINT chk_notif_type CHECK (type IN (
        'FIBER_REQUEST_SUBMITTED', 'NEW_TENANT_ONBOARDED',
        'FIBER_REQUEST_APPROVED', 'FIBER_REQUEST_REJECTED',
        'BATCH_QC_COMPLETED', 'BATCH_QUARANTINE', 'BATCH_OVERRIDE_REQUIRED',
        'BATCH_NO_QUALITY_STANDARD'
    )),
    CONSTRAINT chk_notif_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH'))
);

CREATE UNIQUE INDEX idx_notif_uid ON common_communication.common_notification(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_notif_tenant ON common_communication.common_notification(tenant_id);
CREATE INDEX idx_notif_recipient ON common_communication.common_notification(recipient_id);
CREATE INDEX idx_notif_tenant_unread ON common_communication.common_notification(tenant_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notif_recipient_unread ON common_communication.common_notification(recipient_id, is_read) WHERE recipient_id IS NOT NULL AND is_read = FALSE;
CREATE INDEX idx_notif_created_at ON common_communication.common_notification(created_at);

-- [NOTIFICATION] module migration tamamlandı.
-- Tablo sayısı: 5
-- Toplam index sayısı: 22+
