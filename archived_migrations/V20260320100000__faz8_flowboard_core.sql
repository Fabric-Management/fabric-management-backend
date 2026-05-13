-- =============================================================================
-- Faz 8.1: FlowBoard — Operasyonel Görev Yönetimi (Çekirdek)
-- Docs: 07-flowboard/board-task.md (v2.0)
-- Created: 2026-03-20
-- Schema: flowboard
-- Tables: board, board_group, board_view, task, task_assignee,
--         task_label, task_label_assignment
--
-- TODO-TECHDEBT [M1] Rollback:
--   Bu migration'ı geri almak için: DROP SCHEMA flowboard CASCADE;
--   DİKKAT: Flyway Community Edition otomatik rollback desteklemez.
--   Production'da rollback gerekirse bu SQL'i manuel çalıştırın.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS flowboard;

-- =============================================================================
-- 8.1.1 — BOARD
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.board
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    name                VARCHAR(255) NOT NULL,
    board_type          VARCHAR(30)  NOT NULL,   -- FIBER/YARN/FABRIC/DYE_FINISHING/TRADING/GLOBAL
    wip_limit_default   INT          NOT NULL    DEFAULT 5,
    default_view_type   VARCHAR(20)  NOT NULL    DEFAULT 'KANBAN',
    description         TEXT,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_board_type CHECK (board_type IN ('FIBER','YARN','FABRIC','DYE_FINISHING','TRADING','GLOBAL')),
    CONSTRAINT chk_board_view_type CHECK (default_view_type IN ('KANBAN','TABLE','TIMELINE','CALENDAR','WORKLOAD')),
    CONSTRAINT uq_board_type_tenant UNIQUE (tenant_id, board_type)
);

-- =============================================================================
-- 8.1.2 — BOARD GROUP
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.board_group
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    board_id        UUID         NOT NULL REFERENCES flowboard.board (id),
    name            VARCHAR(255) NOT NULL,
    color           VARCHAR(7)   NOT NULL    DEFAULT '#3498DB', -- HEX
    display_order   INT          NOT NULL    DEFAULT 0,
    is_collapsed    BOOLEAN      NOT NULL    DEFAULT FALSE,
    group_type      VARCHAR(20)  NOT NULL    DEFAULT 'MANUAL', -- STATUS_BASED/DEADLINE_BASED/MANUAL/CUSTOM
    filter_criteria JSONB,                                     -- CUSTOM gruplama kuralı
    is_active       BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by      UUID,
    version         BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_board_group_type CHECK (group_type IN ('STATUS_BASED','DEADLINE_BASED','MANUAL','CUSTOM'))
);

-- =============================================================================
-- 8.1.3 — BOARD VIEW
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.board_view
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    board_id            UUID         NOT NULL REFERENCES flowboard.board (id),
    name                VARCHAR(255) NOT NULL,
    view_type           VARCHAR(20)  NOT NULL,   -- KANBAN/TABLE/TIMELINE/CALENDAR/WORKLOAD
    config              JSONB,                   -- filtreler, sıralama, gizli alanlar
    is_default          BOOLEAN      NOT NULL    DEFAULT FALSE,
    created_by_user_id  UUID,                   -- FK → common_user (user)
    is_shared           BOOLEAN      NOT NULL    DEFAULT TRUE,
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_board_view_view_type CHECK (view_type IN ('KANBAN','TABLE','TIMELINE','CALENDAR','WORKLOAD'))
);

-- =============================================================================
-- 8.1.4 — TASK
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    task_number     VARCHAR(20)  NOT NULL,       -- TSK-0001 (tenant-scoped unique)
    board_id        UUID         NOT NULL REFERENCES flowboard.board (id),
    board_group_id  UUID         REFERENCES flowboard.board_group (id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    task_type       VARCHAR(30)  NOT NULL,   -- PLANNING/PRODUCTION/QUALITY/...
    module_type     VARCHAR(30)  NOT NULL,   -- FIBER/YARN/FABRIC/DYE_FINISHING/GENERAL
    priority        VARCHAR(10)  NOT NULL    DEFAULT 'MEDIUM', -- LOW/MEDIUM/HIGH/CRITICAL
    priority_score  INT          NOT NULL    DEFAULT 0,
    deadline        DATE,
    is_deadline_warning_fired BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_hours NUMERIC(6,2),
    actual_hours    NUMERIC(6,2)             DEFAULT 0,        -- TaskTimeEntry'lerden toplam
    status          VARCHAR(20)  NOT NULL    DEFAULT 'BACKLOG',
    entity_type     VARCHAR(50),             -- SALES_ORDER/WORK_ORDER/BATCH/...
    entity_id       UUID,                   -- polimorfik FK
    source_type     VARCHAR(30)  DEFAULT 'MANUAL',
    source_id       UUID,
    started_at      TIMESTAMPTZ,            -- ilk IN_PROGRESS geçişi
    completed_at    TIMESTAMPTZ,            -- DONE geçişi
    is_active       BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by      UUID,
    version         BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_task_type CHECK (task_type IN (
        'PLANNING','PRODUCTION','QUALITY','WAREHOUSE','SHIPMENT',
        'APPROVAL','RECIPE_ASSIGNMENT','PROCUREMENT','COSTING',
        'SAMPLE','RETURN','STOCK_COUNT','MAINTENANCE','GENERAL'
    )),
    CONSTRAINT chk_task_module_type CHECK (module_type IN ('FIBER','YARN','FABRIC','DYE_FINISHING','GENERAL')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_task_status CHECK (status IN (
        'BACKLOG','TODO','IN_PROGRESS','IN_REVIEW','DONE','BLOCKED','CANCELLED'
    ))
);

-- Task number sequence (TSK-0001 formatı)
CREATE SEQUENCE IF NOT EXISTS flowboard.task_number_seq
    START 1 INCREMENT 1 MINVALUE 1 NO MAXVALUE;

-- =============================================================================
-- 8.1.5 — TASK ASSIGNEE
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_assignee
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    task_id         UUID         NOT NULL REFERENCES flowboard.task (id),
    user_id         UUID,                   -- FK → common_user
    department_id   UUID,                   -- FK → common_department
    assigned_by     VARCHAR(10)  NOT NULL,   -- SYSTEM/MANAGER/SELF
    assigned_at     TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    is_active       BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by      UUID,
    version         BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_task_assignee_by CHECK (assigned_by IN ('SYSTEM','MANAGER','SELF')),
    CONSTRAINT chk_task_assignee_target CHECK (user_id IS NOT NULL OR department_id IS NOT NULL)
);

-- =============================================================================
-- 8.1.6 — TASK LABEL
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_label
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    uid         VARCHAR(100) UNIQUE,
    name        VARCHAR(100) NOT NULL,
    color       VARCHAR(7)   NOT NULL    DEFAULT '#3498DB', -- HEX
    icon        VARCHAR(10),             -- Emoji — 🔴, ⭐
    board_id    UUID         REFERENCES flowboard.board (id), -- NULL = global
    is_active   BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by  UUID,
    version     BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT uq_task_label_name_board UNIQUE (tenant_id, name, board_id)
);

-- =============================================================================
-- 8.1.7 — TASK LABEL ASSIGNMENT (M:N)
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_label_assignment
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    task_id     UUID         NOT NULL REFERENCES flowboard.task (id),
    label_id    UUID         NOT NULL REFERENCES flowboard.task_label (id),
    created_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by  UUID,
    CONSTRAINT uq_task_label_assignment UNIQUE (task_id, label_id)
);

-- =============================================================================
-- INDEXES
-- =============================================================================

-- Board
CREATE INDEX IF NOT EXISTS idx_board_tenant ON flowboard.board (tenant_id);
CREATE INDEX IF NOT EXISTS idx_board_type ON flowboard.board (board_type);
CREATE INDEX IF NOT EXISTS idx_board_active ON flowboard.board (tenant_id, is_active) WHERE is_active = TRUE;

-- Board Group
CREATE INDEX IF NOT EXISTS idx_board_group_board ON flowboard.board_group (board_id);
CREATE INDEX IF NOT EXISTS idx_board_group_order ON flowboard.board_group (board_id, display_order);

-- Board View
CREATE INDEX IF NOT EXISTS idx_board_view_board ON flowboard.board_view (board_id);
CREATE INDEX IF NOT EXISTS idx_board_view_default ON flowboard.board_view (board_id, is_default) WHERE is_default = TRUE;

-- Task — most critical indexes
CREATE INDEX IF NOT EXISTS idx_task_board ON flowboard.task (board_id);
CREATE INDEX IF NOT EXISTS idx_task_board_group ON flowboard.task (board_group_id) WHERE board_group_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_status ON flowboard.task (status);
CREATE INDEX IF NOT EXISTS idx_task_board_status ON flowboard.task (board_id, status) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_task_priority_score ON flowboard.task (board_id, priority_score DESC) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_task_deadline ON flowboard.task (deadline) WHERE deadline IS NOT NULL AND is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_task_entity ON flowboard.task (entity_type, entity_id) WHERE entity_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_tenant ON flowboard.task (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_task_number ON flowboard.task (tenant_id, task_number);

-- Task Assignee
CREATE INDEX IF NOT EXISTS idx_task_assignee_task ON flowboard.task_assignee (task_id);
CREATE INDEX IF NOT EXISTS idx_task_assignee_user ON flowboard.task_assignee (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_assignee_dept ON flowboard.task_assignee (department_id) WHERE department_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_assignee_active ON flowboard.task_assignee (user_id, is_active) WHERE is_active = TRUE;

-- Task Label
CREATE INDEX IF NOT EXISTS idx_task_label_tenant ON flowboard.task_label (tenant_id);
CREATE INDEX IF NOT EXISTS idx_task_label_board ON flowboard.task_label (board_id) WHERE board_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_label_global ON flowboard.task_label (tenant_id, is_active)
    WHERE board_id IS NULL AND is_active = TRUE;

-- Task Label Assignment
CREATE INDEX IF NOT EXISTS idx_task_label_assign_task ON flowboard.task_label_assignment (task_id);
CREATE INDEX IF NOT EXISTS idx_task_label_assign_label ON flowboard.task_label_assignment (label_id);
