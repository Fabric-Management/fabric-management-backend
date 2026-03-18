-- =============================================================================
-- Faz 8.2: FlowBoard — SmartTaskGenerator + AutomationEngine
-- Docs: 07-flowboard/smart-task-generator.md (v2.0)
-- Created: 2026-03-20
-- Schema: flowboard
-- Tables: task_template, automation_rule
--
-- TODO-TECHDEBT [M1] Rollback:
--   Bu migration'ı geri almak için:
--   DROP TABLE IF EXISTS flowboard.automation_rule;
--   DROP TABLE IF EXISTS flowboard.task_template;
-- =============================================================================

-- =============================================================================
-- 8.2.1 — TASK TEMPLATE
-- Event → Task eşleme şablonları
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.task_template
(
    id                      UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    uid                     VARCHAR(100) UNIQUE,
    event_type              VARCHAR(100) NOT NULL,   -- "SalesOrderConfirmed", "BatchQcFailed"
    title_template          VARCHAR(500) NOT NULL,   -- "{salesOrder.orderNumber}" placeholder'lar
    task_type               VARCHAR(30)  NOT NULL,
    module_type             VARCHAR(30),             -- NULL = tüm modüller
    default_priority        VARCHAR(10)  NOT NULL    DEFAULT 'MEDIUM',
    default_assignee_role   VARCHAR(20)  NOT NULL    DEFAULT 'ANY',
    estimated_hours         NUMERIC(6,2),
    checklist_template      TEXT,                    -- JSONB format: [{"title":"...","order":1}]
    auto_labels             TEXT,                    -- JSONB format: ["URGENT","VIP_CLIENT"]
    is_active               BOOLEAN      NOT NULL    DEFAULT TRUE,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by              UUID,
    updated_at              TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by              UUID,
    version                 BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_task_template_task_type CHECK (task_type IN (
        'PLANNING','PRODUCTION','QUALITY','WAREHOUSE','SHIPMENT',
        'APPROVAL','RECIPE_ASSIGNMENT','PROCUREMENT','COSTING',
        'SAMPLE','RETURN','STOCK_COUNT','MAINTENANCE','GENERAL'
    )),
    CONSTRAINT chk_task_template_priority CHECK (default_priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_task_template_assignee_role CHECK (default_assignee_role IN ('DEPARTMENT_ADMIN','MANAGER','ANY'))
);

-- =============================================================================
-- 8.2.2 — AUTOMATION RULE
-- If-Then-That otomasyon kuralları
-- =============================================================================

CREATE TABLE IF NOT EXISTS flowboard.automation_rule
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    uid                 VARCHAR(100) UNIQUE,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    trigger_type        VARCHAR(40)  NOT NULL,
    trigger_config      TEXT         NOT NULL,    -- JSONB: {"fromStatus":"IN_PROGRESS","toStatus":"DONE"}
    condition_config    TEXT,                     -- JSONB: {"taskType":"QUALITY"} — null = her zaman
    action_type         VARCHAR(30)  NOT NULL,
    action_config       TEXT         NOT NULL,    -- JSONB: {"newStatus":"IN_REVIEW"}
    board_id            UUID         REFERENCES flowboard.board (id),  -- null = global
    is_active           BOOLEAN      NOT NULL    DEFAULT TRUE,
    execution_count     BIGINT       NOT NULL    DEFAULT 0,
    last_executed_at    TIMESTAMPTZ,
    created_by_user_id  UUID,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT       NOT NULL    DEFAULT 0,
    CONSTRAINT chk_automation_trigger_type CHECK (trigger_type IN (
        'STATUS_CHANGED','DEADLINE_APPROACHING','TASK_ASSIGNED',
        'TASK_UNASSIGNED_TOO_LONG','LABEL_ADDED','LABEL_REMOVED',
        'CHECKLIST_COMPLETED','TIMER_EXCEEDED','WIP_EXCEEDED','PRIORITY_CHANGED'
    )),
    CONSTRAINT chk_automation_action_type CHECK (action_type IN (
        'CHANGE_STATUS','ASSIGN_USER','ASSIGN_DEPARTMENT','NOTIFY_USER',
        'NOTIFY_MANAGER','CREATE_TASK','ADD_LABEL','REMOVE_LABEL',
        'UPDATE_PRIORITY','ESCALATE'
    ))
);

-- =============================================================================
-- INDEXES
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_task_template_event_type ON flowboard.task_template (event_type, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_task_template_tenant    ON flowboard.task_template (tenant_id);

CREATE INDEX IF NOT EXISTS idx_automation_rule_board   ON flowboard.automation_rule (board_id) WHERE board_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_automation_rule_trigger ON flowboard.automation_rule (trigger_type, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_automation_rule_global  ON flowboard.automation_rule (is_active, trigger_type) WHERE board_id IS NULL AND is_active = TRUE;

-- =============================================================================
-- SEED DATA — 15 TaskTemplate (Global — tenant_id gerekli ama seed null değil)
-- NOT: tenant_id alanı seed'de sistem UUID'i ile doldurulur.
--      Gerçek tenant verileri onboard sırasında kopyalanır.
--      Bu seed'ler template_id ile ilişkilendirilmek üzere saklanır.
-- =============================================================================

-- Sistem seed tenant UUID (sabit — tüm tenant'lar için referans)
DO $$
DECLARE
    seed_tenant UUID := '00000000-0000-0000-0000-000000000001';
BEGIN

-- 1. SalesOrderConfirmed → PLANNING
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'SalesOrderConfirmed', 'Planlama — {salesOrder.orderNumber}', 'PLANNING', 'HIGH', 'MANAGER', 2.0)
ON CONFLICT DO NOTHING;

-- 2. WorkOrderPendingApproval → APPROVAL
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'WorkOrderPendingApproval', 'WO onay — {workOrder.orderNumber}', 'APPROVAL', 'HIGH', 'MANAGER', 0.5)
ON CONFLICT DO NOTHING;

-- 3. WorkOrderApproved → PRODUCTION
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'WorkOrderApproved', 'Üretim başlat — {workOrder.orderNumber}', 'PRODUCTION', 'MEDIUM', 'DEPARTMENT_ADMIN', 8.0)
ON CONFLICT DO NOTHING;

-- 4. BatchQcPending → QUALITY
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'BatchQcPending', 'QC kontrol — {batch.batchNumber}', 'QUALITY', 'MEDIUM', 'DEPARTMENT_ADMIN', 1.0)
ON CONFLICT DO NOTHING;

-- 5. BatchQcFailed → QUALITY (CRITICAL)
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours, auto_labels)
VALUES (seed_tenant, 'BatchQcFailed', 'QC başarısız — yeniden işlem — {batch.batchNumber}', 'QUALITY', 'CRITICAL', 'DEPARTMENT_ADMIN', 4.0, '["URGENT","REWORK"]')
ON CONFLICT DO NOTHING;

-- 6. GoodsReceiptConfirmed → WAREHOUSE
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'GoodsReceiptConfirmed', 'Depo yerleştirme — {goodsReceipt.receiptNumber}', 'WAREHOUSE', 'MEDIUM', 'DEPARTMENT_ADMIN', 1.5)
ON CONFLICT DO NOTHING;

-- 7. SalesOrderInWarehouse → SHIPMENT
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'SalesOrderInWarehouse', 'Sevkiyat hazırla — {salesOrder.orderNumber}', 'SHIPMENT', 'HIGH', 'DEPARTMENT_ADMIN', 2.0)
ON CONFLICT DO NOTHING;

-- 8. RecipeAssignmentNeeded → RECIPE_ASSIGNMENT
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'RecipeAssignmentNeeded', 'Manuel recipe seç — {salesOrderLine.productDesc}', 'RECIPE_ASSIGNMENT', 'HIGH', 'MANAGER', 1.0)
ON CONFLICT DO NOTHING;

-- 9. MinStockAlert → PROCUREMENT
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours, auto_labels)
VALUES (seed_tenant, 'MinStockAlert', 'Stok kritik — {material.name} temin et', 'PROCUREMENT', 'HIGH', 'MANAGER', 2.0, '["URGENT"]')
ON CONFLICT DO NOTHING;

-- 10. PoDeliveryLate → PROCUREMENT
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'PoDeliveryLate', 'PO gecikti — {purchaseOrder.poNumber} takip et', 'PROCUREMENT', 'HIGH', 'MANAGER', 1.0)
ON CONFLICT DO NOTHING;

-- 11. CostVarianceDetected → COSTING
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'CostVarianceDetected', 'Maliyet sapması — {costCalculation.referenceNumber}', 'COSTING', 'MEDIUM', 'MANAGER', 1.5)
ON CONFLICT DO NOTHING;

-- 12. ReturnRateExceeded → RETURN
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'ReturnRateExceeded', 'İade oranı aşıldı — {tradingPartner.companyName}', 'RETURN', 'HIGH', 'MANAGER', 2.0)
ON CONFLICT DO NOTHING;

-- 13. ApprovalPending → APPROVAL
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'ApprovalPending', 'Onay bekliyor — {approvalRequest.referenceNumber}', 'APPROVAL', 'HIGH', 'MANAGER', 0.5)
ON CONFLICT DO NOTHING;

-- 14. UserPromotionReady → APPROVAL
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'UserPromotionReady', 'Kullanıcı yükseltme — {user.displayName}', 'APPROVAL', 'MEDIUM', 'MANAGER', 0.5)
ON CONFLICT DO NOTHING;

-- 15. SupplierLicenseExpiringSoon → GENERAL
INSERT INTO flowboard.task_template (tenant_id, event_type, title_template, task_type, default_priority, default_assignee_role, estimated_hours)
VALUES (seed_tenant, 'SupplierLicenseExpiringSoon', 'Lisans yenileme — {tradingPartner.companyName}', 'GENERAL', 'MEDIUM', 'MANAGER', 1.0)
ON CONFLICT DO NOTHING;

-- =============================================================================
-- SEED DATA — 8 AutomationRule (Global — board_id NULL)
-- =============================================================================

-- 1. QC bitti → depo görevi
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'QC bitti → depo görevi aç',
        'STATUS_CHANGED',
        '{"fromStatus": "IN_PROGRESS", "toStatus": "DONE"}',
        '{"taskType": "QUALITY"}',
        'CREATE_TASK',
        '{"taskType": "WAREHOUSE", "titleTemplate": "Depo: {task.title}"}')
ON CONFLICT DO NOTHING;

-- 2. Kritik deadline uyarısı
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'Kritik deadline yaklaşıyor',
        'DEADLINE_APPROACHING',
        '{"hoursBeforeDeadline": 24}',
        '{"priority": ["HIGH", "CRITICAL"]}',
        'NOTIFY_MANAGER',
        '{"message": "Task deadline 24 saat kaldı: {task.title}"}')
ON CONFLICT DO NOTHING;

-- 3. WIP aşımı uyarısı
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'WIP limiti aşıldı bildirimi',
        'WIP_EXCEEDED',
        '{}',
        NULL,
        'NOTIFY_MANAGER',
        '{"message": "WIP limiti aşıldı! Kullanıcı: {user.displayName}"}')
ON CONFLICT DO NOTHING;

-- 4. Büyük task tıkandı → eskalasyon
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'Büyük task BLOCKED oldu',
        'STATUS_CHANGED',
        '{"toStatus": "BLOCKED"}',
        '{"estimatedHoursGte": 8}',
        'ESCALATE',
        '{"escalateTo": "DEPARTMENT_ADMIN"}')
ON CONFLICT DO NOTHING;

-- 5. VIP etiketi → öncelik artır
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'VIP etiket → öncelik +20',
        'LABEL_ADDED',
        '{"labelName": "VIP_CLIENT"}',
        NULL,
        'UPDATE_PRIORITY',
        '{"priorityBonus": 20}')
ON CONFLICT DO NOTHING;

-- 6. Checklist tamamlandı → IN_REVIEW
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'Checklist tamamlandı → IN_REVIEW',
        'CHECKLIST_COMPLETED',
        '{}',
        NULL,
        'CHANGE_STATUS',
        '{"newStatus": "IN_REVIEW"}')
ON CONFLICT DO NOTHING;

-- 7. Süre aşıldı → URGENT etiketi + manager bildirimi
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        'Süre tahmini %150 aşıldı',
        'TIMER_EXCEEDED',
        '{"thresholdPercent": 150}',
        NULL,
        'ADD_LABEL',
        '{"labelName": "URGENT"}')
ON CONFLICT DO NOTHING;

-- 8. Atanmamış task uyarısı
INSERT INTO flowboard.automation_rule (tenant_id, name, trigger_type, trigger_config, condition_config, action_type, action_config)
VALUES (seed_tenant,
        '2 saat atanmamış task',
        'TASK_UNASSIGNED_TOO_LONG',
        '{"maxHoursUnassigned": 2}',
        NULL,
        'NOTIFY_MANAGER',
        '{"message": "Task 2 saattir atanmadı: {task.title}"}')
ON CONFLICT DO NOTHING;

END $$;
