-- =============================================================================
-- Phase 7: i18n + NotificationHub
-- Docs: i18n.md, notification-hub.md, event-catalog.md
-- Created: 2026-03-19
-- Schemas: i18n, notification
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS i18n;
CREATE SCHEMA IF NOT EXISTS notification;

-- =============================================================================
-- 7.1 — i18n ALTYAPISI
-- =============================================================================

-- Sistem tarafından desteklenen diller
CREATE TABLE IF NOT EXISTS i18n.supported_locale
(
    id         UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL,
    uid        VARCHAR(100) UNIQUE,
    code       VARCHAR(10)  NOT NULL UNIQUE, -- EN, TR, DE, FR, AR
    name       VARCHAR(100) NOT NULL,
    is_rtl     BOOLEAN      NOT NULL    DEFAULT FALSE,
    is_active  BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by UUID,
    version    BIGINT       NOT NULL    DEFAULT 0
);

-- Çeviri anahtarları (modül + keyCode kombinasyonu benzersiz)
CREATE TABLE IF NOT EXISTS i18n.translation_key
(
    id            UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    uid           VARCHAR(100) UNIQUE,
    key_code      VARCHAR(255) NOT NULL,           -- notification.work_order_pending_approval.title
    module        VARCHAR(50)  NOT NULL,           -- NOTIFICATION, PRODUCTION, ORDER
    default_value TEXT         NOT NULL,           -- EN fallback
    description   TEXT,                           -- ne için kullanılıyor
    is_active     BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by    UUID,
    updated_at    TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by    UUID,
    version       BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT uq_translation_key_code UNIQUE (key_code)
);

-- Çeviri değerleri (key + locale başına bir kayıt)
CREATE TABLE IF NOT EXISTS i18n.translation_value
(
    id                 UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    uid                VARCHAR(100) UNIQUE,
    translation_key_id UUID         NOT NULL REFERENCES i18n.translation_key (id),
    locale             VARCHAR(10)  NOT NULL,   -- EN, TR...
    value              TEXT         NOT NULL,
    is_override        BOOLEAN      NOT NULL    DEFAULT FALSE, -- tenant bazlı override
    is_active          BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by         UUID,
    version            BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT uq_translation_value_key_locale UNIQUE (translation_key_id, locale, tenant_id)
);

-- Tenant seviyesi lokalizasyon ayarları
CREATE TABLE IF NOT EXISTS i18n.tenant_locale_config
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL UNIQUE,
    uid               VARCHAR(100) UNIQUE,
    default_locale    VARCHAR(10)  NOT NULL    DEFAULT 'TR',
    supported_locales JSONB        NOT NULL    DEFAULT '["TR","EN"]'::JSONB,
    date_format       VARCHAR(50)  NOT NULL    DEFAULT 'dd.MM.yyyy',
    time_format       VARCHAR(50)  NOT NULL    DEFAULT 'HH:mm',
    timezone          VARCHAR(100) NOT NULL    DEFAULT 'Europe/Istanbul',
    currency          VARCHAR(10)  NOT NULL    DEFAULT 'TRY',
    is_active         BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by        UUID,
    version           BIGINT       NOT NULL    DEFAULT 0
);

-- Kullanıcı seviyesi lokalizasyon ayarları
CREATE TABLE IF NOT EXISTS i18n.user_locale_config
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    uid         VARCHAR(100) UNIQUE,
    user_id     UUID         NOT NULL UNIQUE,
    locale      VARCHAR(10)  NOT NULL    DEFAULT 'TR',
    date_format VARCHAR(50),
    timezone    VARCHAR(100),
    is_active   BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by  UUID,
    version     BIGINT       NOT NULL    DEFAULT 0
);

-- =============================================================================
-- 7.2 — NOTIFICATION HUB
-- =============================================================================

-- Bildirim şablonları (event + kanal başına 1 şablon)
CREATE TABLE IF NOT EXISTS notification.notification_template
(
    id                     UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id              UUID         NOT NULL,
    uid                    VARCHAR(100) UNIQUE,
    event_type             VARCHAR(100) NOT NULL,   -- WORK_ORDER_PENDING_APPROVAL
    channel                VARCHAR(20)  NOT NULL,   -- IN_APP, EMAIL, PUSH
    title_key              VARCHAR(255) NOT NULL,   -- FK → i18n.translation_key.key_code
    body_key               VARCHAR(255) NOT NULL,   -- FK → i18n.translation_key.key_code
    importance             VARCHAR(20)  NOT NULL    DEFAULT 'NORMAL', -- NORMAL, HIGH, CRITICAL
    delivery_type          VARCHAR(20)  NOT NULL    DEFAULT 'INSTANT', -- INSTANT, SCHEDULED, DIGEST
    grouping_window_minutes INT                     DEFAULT 5,
    is_active              BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by             UUID,
    updated_at             TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by             UUID,
    version                BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT uq_notification_template_event_channel UNIQUE (tenant_id, event_type, channel),
    CONSTRAINT chk_notif_template_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH')),
    CONSTRAINT chk_notif_template_importance CHECK (importance IN ('NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_notif_template_delivery CHECK (delivery_type IN ('INSTANT', 'SCHEDULED', 'DIGEST'))
);

-- Gönderim kuyruğu (her bildirim için bir kayıt)
CREATE TABLE IF NOT EXISTS notification.notification_queue
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    uid          VARCHAR(100) UNIQUE,
    recipient_id UUID         NOT NULL,   -- FK → common_user.common_user.id
    event_type   VARCHAR(100) NOT NULL,
    channel      VARCHAR(20)  NOT NULL,
    importance   VARCHAR(20)  NOT NULL    DEFAULT 'NORMAL',
    delivery_type VARCHAR(20) NOT NULL    DEFAULT 'INSTANT',
    scheduled_at TIMESTAMPTZ,
    payload      JSONB        NOT NULL    DEFAULT '{}'::JSONB,  -- event parametreleri
    status       VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',
    retry_count  INT          NOT NULL    DEFAULT 0,
    last_error   TEXT,
    locale       VARCHAR(10)  NOT NULL    DEFAULT 'TR',
    processed_at TIMESTAMPTZ,
    is_active    BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by   UUID,
    updated_at   TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by   UUID,
    version      BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_notif_queue_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notif_queue_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH')),
    CONSTRAINT chk_notif_queue_importance CHECK (importance IN ('NORMAL', 'HIGH', 'CRITICAL'))
);

-- Gönderim logu (gönderilen + kullanıcı etkileşim takibi)
CREATE TABLE IF NOT EXISTS notification.notification_log
(
    id               UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    uid              VARCHAR(100) UNIQUE,
    recipient_id     UUID         NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    channel          VARCHAR(20)  NOT NULL,
    importance       VARCHAR(20)  NOT NULL,
    title            TEXT         NOT NULL,   -- render edilmiş başlık
    body             TEXT         NOT NULL,   -- render edilmiş gövde
    locale           VARCHAR(10)  NOT NULL,
    sent_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    is_read          BOOLEAN      NOT NULL    DEFAULT FALSE,
    read_at          TIMESTAMPTZ,
    is_clicked       BOOLEAN      NOT NULL    DEFAULT FALSE,
    clicked_at       TIMESTAMPTZ,
    action_taken     VARCHAR(30),             -- APPROVED, REJECTED, DISMISSED
    action_taken_at  TIMESTAMPTZ,
    group_id         UUID,                   -- aynı grup bildirimleri birleştirme
    reference_id     UUID,                   -- ilgili entity (workOrder.id, batch.id vb.)
    reference_type   VARCHAR(100),           -- WORK_ORDER, BATCH, PURCHASE_ORDER vb.
    is_active        BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by       UUID,
    version          BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_notif_log_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH')),
    CONSTRAINT chk_notif_log_importance CHECK (importance IN ('NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_notif_log_action CHECK (action_taken IN ('APPROVED', 'REJECTED', 'DISMISSED') OR action_taken IS NULL)
);

-- Kullanıcı bildirim tercihleri (CRITICAL → yok sayılır)
CREATE TABLE IF NOT EXISTS notification.user_notification_preference
(
    id         UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL,
    uid        VARCHAR(100) UNIQUE,
    user_id    UUID         NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    in_app     BOOLEAN      NOT NULL    DEFAULT TRUE,
    email      BOOLEAN      NOT NULL    DEFAULT TRUE,
    push       BOOLEAN      NOT NULL    DEFAULT TRUE,
    is_active  BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by UUID,
    version    BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT uq_user_notif_pref UNIQUE (user_id, event_type)
);

-- =============================================================================
-- INDEXES
-- =============================================================================

-- i18n indexes
CREATE INDEX IF NOT EXISTS idx_translation_key_code ON i18n.translation_key (key_code);
CREATE INDEX IF NOT EXISTS idx_translation_key_module ON i18n.translation_key (module);
CREATE INDEX IF NOT EXISTS idx_translation_value_key ON i18n.translation_value (translation_key_id);
CREATE INDEX IF NOT EXISTS idx_translation_value_locale ON i18n.translation_value (locale);
CREATE INDEX IF NOT EXISTS idx_tenant_locale_config_tenant ON i18n.tenant_locale_config (tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_locale_config_user ON i18n.user_locale_config (user_id);

-- notification indexes
CREATE INDEX IF NOT EXISTS idx_notif_template_event ON notification.notification_template (event_type);
CREATE INDEX IF NOT EXISTS idx_notif_template_tenant ON notification.notification_template (tenant_id);

CREATE INDEX IF NOT EXISTS idx_notif_queue_recipient ON notification.notification_queue (recipient_id);
CREATE INDEX IF NOT EXISTS idx_notif_queue_status ON notification.notification_queue (status);
CREATE INDEX IF NOT EXISTS idx_notif_queue_tenant ON notification.notification_queue (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notif_queue_scheduled ON notification.notification_queue (scheduled_at)
    WHERE scheduled_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_notif_queue_pending ON notification.notification_queue (status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_notif_log_recipient ON notification.notification_log (recipient_id);
CREATE INDEX IF NOT EXISTS idx_notif_log_tenant ON notification.notification_log (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notif_log_unread ON notification.notification_log (recipient_id, is_read)
    WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_notif_log_event_type ON notification.notification_log (event_type);
CREATE INDEX IF NOT EXISTS idx_notif_log_sent_at ON notification.notification_log (sent_at);
CREATE INDEX IF NOT EXISTS idx_notif_log_group ON notification.notification_log (group_id)
    WHERE group_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_notif_pref_user ON notification.user_notification_preference (user_id);
CREATE INDEX IF NOT EXISTS idx_user_notif_pref_event ON notification.user_notification_preference (event_type);

-- =============================================================================
-- SEED: SupportedLocale (sistem geneli, tenant_id = '00000000-0000-0000-0000-000000000001')
-- =============================================================================

INSERT INTO i18n.supported_locale (id, tenant_id, code, name, is_rtl)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'TR', 'Türkçe', FALSE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'EN', 'English', FALSE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'DE', 'Deutsch', FALSE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'FR', 'Français', FALSE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'AR', 'العربية', TRUE)
ON CONFLICT (code) DO NOTHING;
