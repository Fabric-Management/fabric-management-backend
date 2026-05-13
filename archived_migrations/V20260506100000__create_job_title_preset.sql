-- =============================================
-- JobTitle Preset — Lookup Table with CRUD
-- BaseEntity alanları: id, uid, tenant_id, created_at/by, updated_at/by, is_active, deleted_at, version
-- =============================================

CREATE TABLE IF NOT EXISTS common_user.job_title_preset (
    id              UUID PRIMARY KEY,
    uid             VARCHAR(100) NOT NULL UNIQUE,
    tenant_id       UUID NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by      UUID,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,

    job_title_code  VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),

    department_code VARCHAR(50),
    role_code       VARCHAR(50),

    is_system       BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_job_title_tenant_code UNIQUE (tenant_id, job_title_code)
);

CREATE INDEX IF NOT EXISTS idx_jtp_tenant ON common_user.job_title_preset (tenant_id);
CREATE INDEX IF NOT EXISTS idx_jtp_active ON common_user.job_title_preset (tenant_id, is_active);

-- Employee tablosuna FK (cross-schema: human -> common_user)
ALTER TABLE human.human_employee
    ADD COLUMN job_title_preset_id UUID
    REFERENCES common_user.job_title_preset(id) ON DELETE SET NULL;
