-- =========================================================================
-- FAZ 8.4: FLOWBOARD DASHBOARD & RECURRING TASKS (SEEDS)
-- =========================================================================

-- System User Tenant ve Board referansını bul
DO $$
DECLARE
    sys_tenant_id UUID := '00b213b3-8d00-4b36-aaa0-484bd7480e66'::UUID; -- Global varsayılan tenant (FlowBoard Core)
    sys_user_id UUID := '00000000-0000-0000-0000-000000000000'::UUID; -- SystemUser.ID
    default_board_id UUID;
    dashboard_id UUID := gen_random_uuid();
BEGIN
    -- İlk bulduğumuz Board'u kullanalım
    SELECT id INTO default_board_id FROM flowboard.board WHERE tenant_id = sys_tenant_id LIMIT 1;
    
    IF default_board_id IS NOT NULL THEN
    
        -- 1. Default Manager Dashboard
        INSERT INTO flowboard.dashboard_config 
        (id, tenant_id, user_id, name, is_default, layout_jsonb)
        VALUES 
        (dashboard_id, sys_tenant_id, sys_user_id, 'System Manager Dashboard', true, 
         '{"breakpoints": {"lg": 1200, "md": 996}, "layouts": {"lg": [{"i": "widget-1", "x": 0, "y": 0, "w": 4, "h": 2}]}}'::jsonb);

        -- 2. Dashboard Widgets (9 Adet)
        INSERT INTO flowboard.dashboard_widget (id, tenant_id, dashboard_id, widget_type, title, display_order, config_jsonb) VALUES
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'TASK_COUNT', 'Total Open Tasks', 1, '{}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'OVERDUE_TASKS', 'Critical Overdue Tasks', 2, '{"severity": "CRITICAL"}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'WORKLOAD_CHART', 'Team Workload', 3, '{}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'LEADERBOARD', 'Top Performers', 4, '{"limit": 5}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'MY_TASKS', 'My Assigned Tasks', 5, '{}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'RECENT_ACTIVITY', 'Recent Board Activity', 6, '{"limit": 10}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'TEAM_PERFORMANCE', 'Weekly Team Stats', 7, '{}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'BOTTLENECKS', 'Process Bottlenecks', 8, '{}'),
        (gen_random_uuid(), sys_tenant_id, dashboard_id, 'TIME_TRACKING_SUMMARY', 'Timesheet Overview', 9, '{}');

        -- 3. 6 Default Recurring Task Templates
        INSERT INTO flowboard.recurring_task_template
        (id, tenant_id, board_id, title, description, task_type, priority, frequency, is_active, next_trigger_at)
        VALUES
        (gen_random_uuid(), sys_tenant_id, default_board_id, 'Daily Standup Prep', 'Prepare notes for daily standup', 'GENERAL', 'MEDIUM', 'DAILY', true, NOW() + INTERVAL '1 day'),
        (gen_random_uuid(), sys_tenant_id, default_board_id, 'Weekly Equipment Calibration', 'Calibrate production machines', 'MAINTENANCE', 'HIGH', 'WEEKLY', true, NOW() + INTERVAL '7 days'),
        (gen_random_uuid(), sys_tenant_id, default_board_id, 'Monthly Inventory Count', 'Count the main warehouse inventory', 'STOCK_COUNT', 'CRITICAL', 'MONTHLY', true, NOW() + INTERVAL '30 days'),
        (gen_random_uuid(), sys_tenant_id, default_board_id, 'Quarterly Quality Audit', 'Conduct internal ISO quality audit', 'QUALITY', 'HIGH', 'QUARTERLY', true, NOW() + INTERVAL '90 days'),
        (gen_random_uuid(), sys_tenant_id, default_board_id, 'Annual Performance Reviews', 'Annual employee review cycle', 'GENERAL', 'MEDIUM', 'YEARLY', true, NOW() + INTERVAL '365 days'),
        (gen_random_uuid(), sys_tenant_id, default_board_id, 'Post-Production Safety Check', 'Check site after production run', 'GENERAL', 'MEDIUM', 'ON_COMPLETION', true, null);

    END IF;
END $$;
