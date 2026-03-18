-- =============================================================================
-- Faz 8.1 Seeds: FlowBoard — Varsayılan Etiketler
-- Docs: 07-flowboard/board-task.md (v2.0) — Bölüm 6 Varsayılan Etiketler
-- Created: 2026-03-20
-- NOT: Global etiketler (board_id NULL) — sistem tenant'ı 00000000-0000-0000-0000-000000000001
-- =============================================================================

-- Sistem tenant ID
DO $$
DECLARE
    v_sys_tenant UUID := '00000000-0000-0000-0000-000000000001';
BEGIN

    -- =========================================================================
    -- 6 Varsayılan Global Etiket
    -- Belgeye göre PriorityScore bonus'ları için TaskService içinde
    -- sabit olarak tanımlanacak (etiket adına göre).
    -- =========================================================================

    INSERT INTO flowboard.task_label (id, tenant_id, name, color, icon, board_id)
    VALUES
        -- URGENT — +15 priorityScore bonus
        (gen_random_uuid(), v_sys_tenant, 'URGENT',           '#E74C3C', '🔴', NULL),
        -- VIP_CLIENT — +20 priorityScore bonus
        (gen_random_uuid(), v_sys_tenant, 'VIP_CLIENT',       '#9B59B6', '⭐', NULL),
        -- FIRST_ORDER — +10 priorityScore bonus
        (gen_random_uuid(), v_sys_tenant, 'FIRST_ORDER',      '#3498DB', '🆕', NULL),
        -- SAMPLE — +0 priorityScore bonus
        (gen_random_uuid(), v_sys_tenant, 'SAMPLE',           '#2ECC71', '🧪', NULL),
        -- REWORK — +5 priorityScore bonus
        (gen_random_uuid(), v_sys_tenant, 'REWORK',           '#E67E22', '🔄', NULL),
        -- WAITING_EXTERNAL — +0 priorityScore bonus
        (gen_random_uuid(), v_sys_tenant, 'WAITING_EXTERNAL', '#95A5A6', '⏳', NULL)
    ON CONFLICT (tenant_id, name, board_id) DO NOTHING;

END $$;
