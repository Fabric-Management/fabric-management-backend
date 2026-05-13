-- =============================================================================
-- Faz 8.3: FlowBoard — Task Detayları ve Eskalasyon
-- Docs: 07-flowboard/task-details.md, 07-flowboard/escalation.md (v2.0)
-- Schema: flowboard
-- Tables: task_checklist, task_comment, task_activity_log, task_dependency, 
--         task_time_entry, task_attachment, task_reminder, task_relation,
--         escalation_log
-- =============================================================================

-- =============================================================================
-- 1. TASK CHECKLIST
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_checklist
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    title               VARCHAR(255) NOT NULL,
    is_completed        BOOLEAN      NOT NULL    DEFAULT FALSE,
    completed_at        TIMESTAMPTZ,
    completed_by_user_id UUID,
    display_order       INTEGER      NOT NULL    DEFAULT 1,
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_task_checklist_task ON flowboard.task_checklist (task_id);

-- =============================================================================
-- 2. TASK COMMENT
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_comment
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    user_id             UUID         NOT NULL,
    content             TEXT         NOT NULL,
    mentioned_user_ids  TEXT,                   -- JSONB
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_task_comment_task ON flowboard.task_comment (task_id);

-- =============================================================================
-- 3. TASK ACTIVITY LOG
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_activity_log
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    user_id             UUID,                   -- null ise SYSTEM
    action              VARCHAR(40)  NOT NULL,  -- Enum: STATUS_CHANGED, COMMENTED vs.
    old_value           VARCHAR(255),
    new_value           VARCHAR(255),
    metadata            TEXT,                   -- JSONB
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_task_activity_log_task ON flowboard.task_activity_log (task_id);
CREATE INDEX idx_task_activity_log_action ON flowboard.task_activity_log (action);

-- =============================================================================
-- 4. TASK DEPENDENCY
-- =============================================================================
-- Tablo artık BaseEntity extend ediyor
CREATE TABLE IF NOT EXISTS flowboard.task_dependency
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    depends_on_task_id  UUID         NOT NULL REFERENCES flowboard.task (id),
    dependency_type     VARCHAR(20)  NOT NULL,  -- FINISH_TO_START, START_TO_START, PARALLEL
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE UNIQUE INDEX uk_task_dependency ON flowboard.task_dependency (task_id, depends_on_task_id);
CREATE INDEX idx_task_dependency_depends_on ON flowboard.task_dependency (depends_on_task_id);

-- =============================================================================
-- 5. TASK TIME ENTRY
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_time_entry
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    user_id             UUID         NOT NULL,
    started_at          TIMESTAMPTZ  NOT NULL,
    ended_at            TIMESTAMPTZ,
    duration_minutes    INTEGER,
    entry_type          VARCHAR(20)  NOT NULL,  -- TIMER, MANUAL
    note                VARCHAR(500),
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_task_time_entry_task ON flowboard.task_time_entry (task_id);
CREATE INDEX idx_task_time_entry_user ON flowboard.task_time_entry (user_id);
-- Tek aktif timer kontrolü için partial unique index
CREATE UNIQUE INDEX uk_task_time_entry_active_timer 
    ON flowboard.task_time_entry (user_id) 
    WHERE ended_at IS NULL AND deleted_at IS NULL;

-- =============================================================================
-- 6. TASK ATTACHMENT
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_attachment
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    file_name           VARCHAR(500) NOT NULL,
    file_type           VARCHAR(50)  NOT NULL,
    file_size_bytes     BIGINT       NOT NULL,
    storage_path        VARCHAR(1000) NOT NULL,
    uploaded_by_user_id UUID         NOT NULL,
    attachment_type     VARCHAR(20)  NOT NULL,  -- DOCUMENT, IMAGE, REPORT, OTHER
    description         VARCHAR(255),
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_task_attachment_task ON flowboard.task_attachment (task_id);

-- =============================================================================
-- 7. TASK REMINDER
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_reminder
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    user_id             UUID         NOT NULL,
    reminder_type       VARCHAR(30)  NOT NULL,  -- MANUAL, DEADLINE_OFFSET, FOLLOW_UP
    trigger_at          TIMESTAMPTZ  NOT NULL,
    offset_minutes      INTEGER,
    message             VARCHAR(500),
    is_sent             BOOLEAN      NOT NULL    DEFAULT FALSE,
    sent_at             TIMESTAMPTZ,
    channel             VARCHAR(20)  NOT NULL,  -- IN_APP, EMAIL, BOTH
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_task_reminder_task ON flowboard.task_reminder (task_id);
CREATE INDEX idx_task_reminder_trigger ON flowboard.task_reminder (trigger_at) WHERE is_sent = FALSE AND deleted_at IS NULL;

-- =============================================================================
-- 8. TASK RELATION (Cross-Board Link)
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_relation
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    source_task_id      UUID         NOT NULL REFERENCES flowboard.task (id),
    target_task_id      UUID         NOT NULL REFERENCES flowboard.task (id),
    relation_type       VARCHAR(30)  NOT NULL,  -- RELATED, DUPLICATES, CAUSED_BY, PARENT_CHILD
    created_by_user_id  UUID         NOT NULL,
    note                VARCHAR(255),
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE UNIQUE INDEX uk_task_relation ON flowboard.task_relation (source_task_id, target_task_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_task_relation_target ON flowboard.task_relation (target_task_id);

-- =============================================================================
-- 9. ESCALATION LOG
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.escalation_log
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    task_id             UUID         NOT NULL REFERENCES flowboard.task (id),
    escalation_type     VARCHAR(30)  NOT NULL,  -- DEADLINE_PASSED, UNASSIGNED, BLOCKED_TOO_LONG vs
    escalated_to_user_id UUID        NOT NULL,
    message             VARCHAR(500) NOT NULL,
    resolved_at         TIMESTAMPTZ,
    resolved_by_user_id UUID,
    resolution_note     VARCHAR(500),
    deleted_at          TIMESTAMPTZ,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0
);
CREATE INDEX idx_escalation_log_task ON flowboard.escalation_log (task_id);
CREATE INDEX idx_escalation_log_type ON flowboard.escalation_log (escalation_type) WHERE deleted_at IS NULL;
