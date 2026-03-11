-- ============================================================================
-- V079: Common Notification Table - In-App & Email Notifications
-- ============================================================================
-- In-app and email notification entity for platform and tenant-level events.
-- Supports FIBER_REQUEST, BATCH, QC and related notifications.
--
-- tenant_id: target tenant (or SYSTEM_TENANT_ID for platform-level)
-- recipient_id: target user; null = broadcast to all admins of the tenant
-- ============================================================================

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
        'BATCH_QC_COMPLETED', 'BATCH_QUARANTINE', 'BATCH_OVERRIDE_REQUIRED'
    )),
    CONSTRAINT chk_notif_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH'))
);

CREATE UNIQUE INDEX idx_notif_uid ON common_communication.common_notification(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_notif_tenant ON common_communication.common_notification(tenant_id);
CREATE INDEX idx_notif_recipient ON common_communication.common_notification(recipient_id);
CREATE INDEX idx_notif_tenant_unread ON common_communication.common_notification(tenant_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notif_recipient_unread ON common_communication.common_notification(recipient_id, is_read) WHERE recipient_id IS NOT NULL AND is_read = FALSE;
CREATE INDEX idx_notif_created_at ON common_communication.common_notification(created_at);

COMMENT ON TABLE common_communication.common_notification IS 'In-app and email notifications for platform and tenant events';
COMMENT ON COLUMN common_communication.common_notification.tenant_id IS 'Target tenant (SYSTEM_TENANT_ID for platform-level)';
COMMENT ON COLUMN common_communication.common_notification.recipient_id IS 'Target user; null = broadcast to all admins of the tenant';
COMMENT ON COLUMN common_communication.common_notification.reference_type IS 'Related entity type: FIBER_REQUEST, BATCH, QC, etc.';
