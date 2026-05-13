-- =============================================================================
-- Phase 7 Seeds Part 2: Tüm event tipleri için TR+EN çeviri anahtarları
-- Önceki migration'ın (V20260319110000) devamı — CRITICAL dışındaki eventler
-- =============================================================================

DO $$
DECLARE
  sys_tenant UUID := '00000000-0000-0000-0000-000000000001'::UUID;
BEGIN

  -- =========================================================
  -- Translation Keys — HIGH eventler
  -- =========================================================
  INSERT INTO i18n.translation_key (id, tenant_id, key_code, module, default_value, description)
  VALUES
    -- WorkOrderPendingApproval
    (gen_random_uuid(), sys_tenant, 'notification.work_order_pending_approval.title', 'NOTIFICATION',
     'Approval Required: {workOrderNumber}', 'WorkOrderPendingApproval title'),
    (gen_random_uuid(), sys_tenant, 'notification.work_order_pending_approval.body', 'NOTIFICATION',
     'Work order {workOrderNumber} is waiting for your approval.', 'WorkOrderPendingApproval body'),

    -- ApprovalPending
    (gen_random_uuid(), sys_tenant, 'notification.approval_pending.title', 'NOTIFICATION',
     'Approval Required: {entityCode}', 'ApprovalPending title'),
    (gen_random_uuid(), sys_tenant, 'notification.approval_pending.body', 'NOTIFICATION',
     '{entityType} {entityCode} requires your approval.', 'ApprovalPending body'),

    -- ApprovalRejected
    (gen_random_uuid(), sys_tenant, 'notification.approval_rejected.title', 'NOTIFICATION',
     'Rejected: {entityCode}', 'ApprovalRejected title'),
    (gen_random_uuid(), sys_tenant, 'notification.approval_rejected.body', 'NOTIFICATION',
     'Your request {entityCode} was rejected. Reason: {rejectionReason}', 'ApprovalRejected body'),

    -- MinStockAlert
    (gen_random_uuid(), sys_tenant, 'notification.min_stock_alert.title', 'NOTIFICATION',
     'Low Stock: {productCode}', 'MinStockAlert title'),
    (gen_random_uuid(), sys_tenant, 'notification.min_stock_alert.body', 'NOTIFICATION',
     'Product {productCode} - {productName} is below minimum stock level. Current: {currentStock} {unit}, Min: {minimumStock} {unit}.', 'MinStockAlert body'),

    -- PoDeliveryLate
    (gen_random_uuid(), sys_tenant, 'notification.po_delivery_late.title', 'NOTIFICATION',
     'Delivery Overdue: {poNumber}', 'PoDeliveryLate title'),
    (gen_random_uuid(), sys_tenant, 'notification.po_delivery_late.body', 'NOTIFICATION',
     'Purchase order {poNumber} from {supplierName} is {lateDays} day(s) overdue.', 'PoDeliveryLate body'),

    -- RfqDeadlineApproaching
    (gen_random_uuid(), sys_tenant, 'notification.rfq_deadline_approaching.title', 'NOTIFICATION',
     'RFQ Deadline Soon: {rfqNumber}', 'RfqDeadlineApproaching title'),
    (gen_random_uuid(), sys_tenant, 'notification.rfq_deadline_approaching.body', 'NOTIFICATION',
     'RFQ {rfqNumber} deadline is in {hoursRemaining} hour(s). Please review pending responses.', 'RfqDeadlineApproaching body'),

    -- RfqNoResponse
    (gen_random_uuid(), sys_tenant, 'notification.rfq_no_response.title', 'NOTIFICATION',
     'No Response: {supplierName}', 'RfqNoResponse title'),
    (gen_random_uuid(), sys_tenant, 'notification.rfq_no_response.body', 'NOTIFICATION',
     'Supplier {supplierName} has not responded to RFQ {rfqNumber}.', 'RfqNoResponse body'),

    -- =========================================================
    -- Translation Keys — NORMAL eventler
    -- =========================================================

    -- WorkOrderApproved
    (gen_random_uuid(), sys_tenant, 'notification.work_order_approved.title', 'NOTIFICATION',
     'Approved: {workOrderNumber}', 'WorkOrderApproved title'),
    (gen_random_uuid(), sys_tenant, 'notification.work_order_approved.body', 'NOTIFICATION',
     'Work order {workOrderNumber} has been approved. Production can begin.', 'WorkOrderApproved body'),

    -- ApprovalApproved
    (gen_random_uuid(), sys_tenant, 'notification.approval_approved.title', 'NOTIFICATION',
     'Approved: {entityCode}', 'ApprovalApproved title'),
    (gen_random_uuid(), sys_tenant, 'notification.approval_approved.body', 'NOTIFICATION',
     'Your request {entityCode} has been approved.', 'ApprovalApproved body'),

    -- BatchQcPending
    (gen_random_uuid(), sys_tenant, 'notification.batch_qc_pending.title', 'NOTIFICATION',
     'QC Required: Batch {batchCode}', 'BatchQcPending title'),
    (gen_random_uuid(), sys_tenant, 'notification.batch_qc_pending.body', 'NOTIFICATION',
     'Batch {batchCode} is ready for quality control inspection.', 'BatchQcPending body'),

    -- GoodsReceiptConfirmed
    (gen_random_uuid(), sys_tenant, 'notification.goods_receipt_confirmed.title', 'NOTIFICATION',
     'Goods Received: {receiptNumber}', 'GoodsReceiptConfirmed title'),
    (gen_random_uuid(), sys_tenant, 'notification.goods_receipt_confirmed.body', 'NOTIFICATION',
     'Receipt {receiptNumber} confirmed. {itemCount} item(s) added to stock.', 'GoodsReceiptConfirmed body'),

    -- PoConfirmed
    (gen_random_uuid(), sys_tenant, 'notification.po_confirmed.title', 'NOTIFICATION',
     'PO Confirmed: {poNumber}', 'PoConfirmed title'),
    (gen_random_uuid(), sys_tenant, 'notification.po_confirmed.body', 'NOTIFICATION',
     'Purchase order {poNumber} has been confirmed by supplier.', 'PoConfirmed body'),

    -- PoPartiallyReceived
    (gen_random_uuid(), sys_tenant, 'notification.po_partially_received.title', 'NOTIFICATION',
     'Partial Delivery: {poNumber}', 'PoPartiallyReceived title'),
    (gen_random_uuid(), sys_tenant, 'notification.po_partially_received.body', 'NOTIFICATION',
     '{receivedItemCount} of {totalItemCount} items received for PO {poNumber}.', 'PoPartiallyReceived body'),

    -- ReturnRateExceeded (CRITICAL)
    (gen_random_uuid(), sys_tenant, 'notification.return_rate_exceeded.title', 'NOTIFICATION',
     'CRITICAL: Return Rate Exceeded — {supplierName}', 'ReturnRateExceeded title'),
    (gen_random_uuid(), sys_tenant, 'notification.return_rate_exceeded.body', 'NOTIFICATION',
     'Supplier {supplierName} return rate is {returnRate}% (threshold: {thresholdRate}%) over {periodDays} days.', 'ReturnRateExceeded body')

  ON CONFLICT (key_code) DO NOTHING;

  -- =========================================================
  -- TR Translations
  -- =========================================================
  INSERT INTO i18n.translation_value (id, tenant_id, translation_key_id, locale, value, is_override)
  SELECT gen_random_uuid(), sys_tenant, tk.id, 'TR',
    CASE tk.key_code
      -- WorkOrderPendingApproval
      WHEN 'notification.work_order_pending_approval.title'  THEN 'Onay Gerekiyor: {workOrderNumber}'
      WHEN 'notification.work_order_pending_approval.body'   THEN '{workOrderNumber} numaralı iş emri onayınızı bekliyor.'
      -- ApprovalPending
      WHEN 'notification.approval_pending.title'             THEN 'Onay Gerekiyor: {entityCode}'
      WHEN 'notification.approval_pending.body'              THEN '{entityType} {entityCode} onayınızı bekliyor.'
      -- ApprovalRejected
      WHEN 'notification.approval_rejected.title'            THEN 'Reddedildi: {entityCode}'
      WHEN 'notification.approval_rejected.body'             THEN '{entityCode} talebiniz reddedildi. Neden: {rejectionReason}'
      -- ApprovalApproved
      WHEN 'notification.approval_approved.title'            THEN 'Onaylandı: {entityCode}'
      WHEN 'notification.approval_approved.body'             THEN '{entityCode} talebiniz onaylandı.'
      -- MinStockAlert
      WHEN 'notification.min_stock_alert.title'              THEN 'Düşük Stok: {productCode}'
      WHEN 'notification.min_stock_alert.body'               THEN '{productCode} - {productName} malzemesi minimum stok seviyesinin altında. Mevcut: {currentStock} {unit}, Min: {minimumStock} {unit}.'
      -- PoDeliveryLate
      WHEN 'notification.po_delivery_late.title'             THEN 'Teslimat Gecikti: {poNumber}'
      WHEN 'notification.po_delivery_late.body'              THEN '{supplierName} tedarikçisinden {poNumber} numaralı PO {lateDays} gün gecikmeli.'
      -- RfqDeadlineApproaching
      WHEN 'notification.rfq_deadline_approaching.title'     THEN 'RFQ Deadline Yaklaşıyor: {rfqNumber}'
      WHEN 'notification.rfq_deadline_approaching.body'      THEN '{rfqNumber} RFQ''nin deadline''ına {hoursRemaining} saat kaldı. Bekleyen yanıtları inceleyin.'
      -- RfqNoResponse
      WHEN 'notification.rfq_no_response.title'              THEN 'Yanıt Yok: {supplierName}'
      WHEN 'notification.rfq_no_response.body'               THEN '{supplierName} tedarikçisi {rfqNumber} RFQ için yanıt vermedi.'
      -- WorkOrderApproved
      WHEN 'notification.work_order_approved.title'          THEN 'Onaylandı: {workOrderNumber}'
      WHEN 'notification.work_order_approved.body'           THEN '{workOrderNumber} numaralı iş emri onaylandı. Üretim başlayabilir.'
      -- BatchQcPending
      WHEN 'notification.batch_qc_pending.title'             THEN 'KK Gerekiyor: Parti {batchCode}'
      WHEN 'notification.batch_qc_pending.body'              THEN '{batchCode} partisi kalite kontrol incelemesine hazır.'
      -- GoodsReceiptConfirmed
      WHEN 'notification.goods_receipt_confirmed.title'      THEN 'Mal Teslim Alındı: {receiptNumber}'
      WHEN 'notification.goods_receipt_confirmed.body'       THEN '{receiptNumber} girişi onaylandı. {itemCount} kalem stoğa eklendi.'
      -- PoConfirmed
      WHEN 'notification.po_confirmed.title'                 THEN 'PO Onaylandı: {poNumber}'
      WHEN 'notification.po_confirmed.body'                  THEN '{poNumber} numaralı satın alma siparişi tedarikçi tarafından onaylandı.'
      -- PoPartiallyReceived
      WHEN 'notification.po_partially_received.title'        THEN 'Kısmi Teslim: {poNumber}'
      WHEN 'notification.po_partially_received.body'         THEN '{poNumber} PO için {receivedItemCount}/{totalItemCount} kalem teslim alındı.'
      -- ReturnRateExceeded
      WHEN 'notification.return_rate_exceeded.title'         THEN 'KRİTİK: İade Oranı Aşıldı — {supplierName}'
      WHEN 'notification.return_rate_exceeded.body'          THEN '{supplierName} tedarikçisinin iade oranı {periodDays} günde %{returnRate} (eşik: %{thresholdRate}).'
    END,
    FALSE
  FROM i18n.translation_key tk
  WHERE tk.key_code IN (
    'notification.work_order_pending_approval.title', 'notification.work_order_pending_approval.body',
    'notification.approval_pending.title', 'notification.approval_pending.body',
    'notification.approval_rejected.title', 'notification.approval_rejected.body',
    'notification.approval_approved.title', 'notification.approval_approved.body',
    'notification.min_stock_alert.title', 'notification.min_stock_alert.body',
    'notification.po_delivery_late.title', 'notification.po_delivery_late.body',
    'notification.rfq_deadline_approaching.title', 'notification.rfq_deadline_approaching.body',
    'notification.rfq_no_response.title', 'notification.rfq_no_response.body',
    'notification.work_order_approved.title', 'notification.work_order_approved.body',
    'notification.batch_qc_pending.title', 'notification.batch_qc_pending.body',
    'notification.goods_receipt_confirmed.title', 'notification.goods_receipt_confirmed.body',
    'notification.po_confirmed.title', 'notification.po_confirmed.body',
    'notification.po_partially_received.title', 'notification.po_partially_received.body',
    'notification.return_rate_exceeded.title', 'notification.return_rate_exceeded.body'
  )
  ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

END $$;
