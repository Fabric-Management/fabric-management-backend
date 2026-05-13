-- =============================================================================
-- Phase 7 Seeds: TranslationKeys + NotificationTemplates (CRITICAL events önce)
-- Bu script idempotent'tir — tekrar çalıştırılabilir.
-- =============================================================================

-- Not: tenant_id için sistem UUID'sini kulllanıyoruz
-- Gerçek sistemde tenant-specific seed ayrı yapılır.
-- Bu seed global/sistem varsayılan çevirilerdir.

DO $$
DECLARE
  sys_tenant UUID := '00000000-0000-0000-0000-000000000001'::UUID;
  
  -- Translation key IDs
  k_batch_qc_failed_title UUID;
  k_batch_qc_failed_body UUID;
  k_wo_pending_title UUID;
  k_wo_pending_body UUID;
  k_gr_confirmed_title UUID;
  k_gr_confirmed_body UUID;
  k_task_blocked_title UUID;
  k_task_blocked_body UUID;

BEGIN

  -- =========================================================
  -- 1. TRANSLATION KEYS (CRITICAL events)
  -- =========================================================

  INSERT INTO i18n.translation_key (id, tenant_id, key_code, module, default_value, description)
  VALUES
    (gen_random_uuid(), sys_tenant, 'notification.batch_qc_failed.title', 'NOTIFICATION',
     'QC Failed: Batch {batchCode}', 'BatchQcFailed event title'),
    (gen_random_uuid(), sys_tenant, 'notification.batch_qc_failed.body', 'NOTIFICATION',
     'Batch {batchCode} failed quality control. Immediate action required.', 'BatchQcFailed event body'),
    (gen_random_uuid(), sys_tenant, 'notification.work_order_pending_approval.title', 'NOTIFICATION',
     'Approval Required: {workOrderNumber}', 'WorkOrderPendingApproval title'),
    (gen_random_uuid(), sys_tenant, 'notification.work_order_pending_approval.body', 'NOTIFICATION',
     'Work order {workOrderNumber} is waiting for your approval.', 'WorkOrderPendingApproval body'),
    (gen_random_uuid(), sys_tenant, 'notification.goods_receipt_confirmed.title', 'NOTIFICATION',
     'Goods Received: {receiptNumber}', 'GoodsReceiptConfirmed title'),
    (gen_random_uuid(), sys_tenant, 'notification.goods_receipt_confirmed.body', 'NOTIFICATION',
     'Receipt {receiptNumber} confirmed. {itemCount} item(s) added to stock.', 'GoodsReceiptConfirmed body'),
    (gen_random_uuid(), sys_tenant, 'notification.task_blocked.title', 'NOTIFICATION',
     'Task Blocked: {taskTitle}', 'TaskBlocked title'),
    (gen_random_uuid(), sys_tenant, 'notification.task_blocked.body', 'NOTIFICATION',
     'Task "{taskTitle}" has been blocked. Reason: {reason}', 'TaskBlocked body')
  ON CONFLICT (key_code) DO NOTHING;

  -- =========================================================
  -- 2. TR TRANSLATIONS (CRITICAL events)
  -- =========================================================

  INSERT INTO i18n.translation_value (id, tenant_id, translation_key_id, locale, value, is_override)
  SELECT gen_random_uuid(), sys_tenant, tk.id, 'TR',
    CASE tk.key_code
      WHEN 'notification.batch_qc_failed.title'                  THEN 'KK Başarısız: Parti {batchCode}'
      WHEN 'notification.batch_qc_failed.body'                   THEN '{batchCode} partisi kalite kontrolünü geçemedi. Acil müdahale gerekiyor.'
      WHEN 'notification.work_order_pending_approval.title'       THEN 'Onay Gerekiyor: {workOrderNumber}'
      WHEN 'notification.work_order_pending_approval.body'        THEN '{workOrderNumber} numaralı iş emri onayınızı bekliyor.'
      WHEN 'notification.goods_receipt_confirmed.title'           THEN 'Mal Teslim Alındı: {receiptNumber}'
      WHEN 'notification.goods_receipt_confirmed.body'            THEN '{receiptNumber} girişi onaylandı. {itemCount} kalem stoğa eklendi.'
      WHEN 'notification.task_blocked.title'                      THEN 'Görev Engellendi: {taskTitle}'
      WHEN 'notification.task_blocked.body'                       THEN '"{taskTitle}" görevi engellendi. Neden: {reason}'
    END,
    FALSE
  FROM i18n.translation_key tk
  WHERE tk.key_code IN (
    'notification.batch_qc_failed.title', 'notification.batch_qc_failed.body',
    'notification.work_order_pending_approval.title', 'notification.work_order_pending_approval.body',
    'notification.goods_receipt_confirmed.title', 'notification.goods_receipt_confirmed.body',
    'notification.task_blocked.title', 'notification.task_blocked.body'
  )
  ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

END $$;
