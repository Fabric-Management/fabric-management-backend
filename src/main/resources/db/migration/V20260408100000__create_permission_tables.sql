-- =============================================================================
-- Flyway Migration
-- Task: Departman Kodlarını Standartlaştır + Permission Tablolarını Oluştur
-- =============================================================================

CREATE TABLE IF NOT EXISTS permission_template (
    id              UUID PRIMARY KEY,
    tenant_id       UUID,                    -- NULL = sistem varsayılanı
    role_code       VARCHAR(50) NOT NULL,
    department_code VARCHAR(50),             -- NULL = tüm departmanlar için geçerli
    resource        VARCHAR(50) NOT NULL,
    action          VARCHAR(20) NOT NULL,
    data_scope      VARCHAR(20) NOT NULL,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permission_override (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    resource        VARCHAR(50) NOT NULL,
    action          VARCHAR(20) NOT NULL,
    data_scope      VARCHAR(20),             -- NULL = erişim kaldırıldı
    reason          TEXT,
    granted_by      UUID NOT NULL,
    expires_at      TIMESTAMP,               -- NULL = süresiz
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permission_template_effective
ON permission_template (
    COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'),
    role_code,
    COALESCE(department_code, '__ALL__'),
    resource,
    action
);

CREATE INDEX IF NOT EXISTS idx_permission_override_user ON permission_override(tenant_id, user_id);
