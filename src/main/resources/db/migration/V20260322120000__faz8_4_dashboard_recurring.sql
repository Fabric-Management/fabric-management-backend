-- =========================================================================
-- FAZ 8.4: FLOWBOARD PERFORMANCE, DASHBOARD & RECURRING TASKS
-- =========================================================================

-- =========================================================================
-- 1. ENUMS
-- =========================================================================
CREATE TYPE flowboard.widget_type AS ENUM (
    'TASK_COUNT',
    'OVERDUE_TASKS',
    'RECENT_ACTIVITY',
    'WORKLOAD_CHART',
    'LEADERBOARD',
    'MY_TASKS',
    'TEAM_PERFORMANCE',
    'BOTTLENECKS',
    'ANALYTICS',
    'TIME_TRACKING_SUMMARY'
);

CREATE TYPE flowboard.workload_status AS ENUM (
    'AVAILABLE',
    'OPTIMAL',
    'OVERLOADED'
);

CREATE TYPE flowboard.recurring_frequency AS ENUM (
    'DAILY',
    'WEEKLY',
    'MONTHLY',
    'QUARTERLY',
    'YEARLY',
    'ON_COMPLETION',
    'CUSTOM_CRON'
);

CREATE TYPE flowboard.badge_type AS ENUM (
    'SPEEDSTER',
    'QUALITY_CHAMPION',
    'PROBLEM_SOLVER',
    'TEAM_PLAYER',
    'CONSISTENT_PERFORMER',
    'NIGHT_OWL',
    'EARLY_BIRD',
    'MULTITASKER'
);

-- =========================================================================
-- 2. DASHBOARD CONFIGURATION
-- =========================================================================
CREATE TABLE IF NOT EXISTS flowboard.dashboard_config (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    user_id UUID NOT NULL, -- Kimin dashboard'u
    name VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    layout_jsonb JSONB, -- React Grid Layout veya custom yerleşim datası
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

-- [K1 FIX] DISTINC → DISTINCT typo düzeltildi
-- [O9 FIX] Partial unique index: tenant/user başına sadece 1 default dashboard olabilir
CREATE UNIQUE INDEX IF NOT EXISTS uk_dashboard_config_user_default
    ON flowboard.dashboard_config(tenant_id, user_id) WHERE is_default = true AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_dashboard_config_t_u ON flowboard.dashboard_config(tenant_id, user_id) WHERE deleted_at IS NULL;

-- =========================================================================
-- 3. DASHBOARD WIDGETS
-- =========================================================================
CREATE TABLE IF NOT EXISTS flowboard.dashboard_widget (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    dashboard_id UUID NOT NULL REFERENCES flowboard.dashboard_config(id),
    widget_type flowboard.widget_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    config_jsonb JSONB, -- Widget'a özel filtre, data source vs.
    display_order INT NOT NULL DEFAULT 1,
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dashboard_widget_t_d ON flowboard.dashboard_widget(tenant_id, dashboard_id) WHERE deleted_at IS NULL;

-- =========================================================================
-- 4. USER PERFORMANCE SNAPSHOT (Gamification)
-- =========================================================================
CREATE TABLE IF NOT EXISTS flowboard.user_performance_snapshot (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    user_id UUID NOT NULL,
    snapshot_date DATE NOT NULL, -- Örn: Haftanın ilk günü veya ay bazlı
    
    completed_tasks INT NOT NULL DEFAULT 0,
    overdue_tasks INT NOT NULL DEFAULT 0,
    total_estimated_hours NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_actual_hours NUMERIC(10,2) NOT NULL DEFAULT 0,
    
    total_points INT NOT NULL DEFAULT 0, -- Aktivite veya zorluk bazlı puan
    earned_badges JSONB, -- Kazanılan rozetler listesi (['SPEEDSTER', 'TEAM_PLAYER'])
    top_badge flowboard.badge_type,
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_user_perf_snap_date UNIQUE (tenant_id, user_id, snapshot_date)
);

CREATE INDEX IF NOT EXISTS idx_user_perf_snap_date ON flowboard.user_performance_snapshot(tenant_id, snapshot_date);

-- =========================================================================
-- 5. RECURRING TASK TEMPLATE
-- =========================================================================
CREATE TABLE IF NOT EXISTS flowboard.recurring_task_template (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    board_id UUID NOT NULL REFERENCES flowboard.board(id),
    
    title VARCHAR(255) NOT NULL,
    description TEXT,
    task_type VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    
    frequency flowboard.recurring_frequency NOT NULL,
    cron_expression VARCHAR(100), -- Sadece CUSTOM_CRON ise dolu
    interval_value INT, -- X günde bir, X haftada bir
    
    target_assignee_id UUID, -- Kime atanacak
    target_group_id UUID, -- Hangi flow grubuna konacak
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    next_trigger_at TIMESTAMP WITH TIME ZONE,
    last_spawned_at TIMESTAMP WITH TIME ZONE,
    last_spawned_task_id UUID,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_recurring_template_trigger ON flowboard.recurring_task_template(tenant_id, is_active, next_trigger_at) WHERE deleted_at IS NULL AND is_active = true;
