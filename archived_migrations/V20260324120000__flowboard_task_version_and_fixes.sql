-- FlowBoard Task: @Version + TaskDependency tenant_id kolonu
ALTER TABLE flowboard.task
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- TaskDependency: tenant_id + BaseEntity alanlari eklendi
ALTER TABLE flowboard.task_dependency
    ADD COLUMN IF NOT EXISTS tenant_id  UUID,
    ADD COLUMN IF NOT EXISTS uid        VARCHAR(80),
    ADD COLUMN IF NOT EXISTS is_active  BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_by UUID,
    ADD COLUMN IF NOT EXISTS created_by UUID;

CREATE INDEX IF NOT EXISTS idx_tdep_tenant ON flowboard.task_dependency(tenant_id);

-- TaskComment: mentioned_user_ids TEXT -> jsonb
ALTER TABLE flowboard.task_comment
    ALTER COLUMN mentioned_user_ids TYPE jsonb USING
        CASE
            WHEN mentioned_user_ids IS NULL THEN NULL
            WHEN mentioned_user_ids = '' THEN '[]'::jsonb
            ELSE mentioned_user_ids::jsonb
        END;
