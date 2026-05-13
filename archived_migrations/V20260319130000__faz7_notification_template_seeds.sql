-- =============================================================================
-- Phase 7 Seeds Part 3: NotificationTemplate kayıtları
-- Her event × her kanal için template oluşturulur.
-- Template olmadan bildirim sistemi çalışmaz!
-- =============================================================================

DO $$
DECLARE
  sys_tenant UUID := '00000000-0000-0000-0000-000000000001'::UUID;
BEGIN

  -- =========================================================
  -- CRITICAL events — tüm kanallar INSTANT
  -- =========================================================

  -- BATCH_QC_FAILED (CRITICAL × 3 kanal)
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'BATCH_QC_FAILED', 'IN_APP',
     'notification.batch_qc_failed.title', 'notification.batch_qc_failed.body',
     'CRITICAL', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'BATCH_QC_FAILED', 'EMAIL',
     'notification.batch_qc_failed.title', 'notification.batch_qc_failed.body',
     'CRITICAL', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'BATCH_QC_FAILED', 'PUSH',
     'notification.batch_qc_failed.title', 'notification.batch_qc_failed.body',
     'CRITICAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- RETURN_RATE_EXCEEDED (CRITICAL × 3 kanal)
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'RETURN_RATE_EXCEEDED', 'IN_APP',
     'notification.return_rate_exceeded.title', 'notification.return_rate_exceeded.body',
     'CRITICAL', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'RETURN_RATE_EXCEEDED', 'EMAIL',
     'notification.return_rate_exceeded.title', 'notification.return_rate_exceeded.body',
     'CRITICAL', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'RETURN_RATE_EXCEEDED', 'PUSH',
     'notification.return_rate_exceeded.title', 'notification.return_rate_exceeded.body',
     'CRITICAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- =========================================================
  -- HIGH events — IN_APP + EMAIL (PUSH opsiyonel)
  -- =========================================================

  -- WORK_ORDER_PENDING_APPROVAL
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'WORK_ORDER_PENDING_APPROVAL', 'IN_APP',
     'notification.work_order_pending_approval.title', 'notification.work_order_pending_approval.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'WORK_ORDER_PENDING_APPROVAL', 'EMAIL',
     'notification.work_order_pending_approval.title', 'notification.work_order_pending_approval.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- APPROVAL_PENDING
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'APPROVAL_PENDING', 'IN_APP',
     'notification.approval_pending.title', 'notification.approval_pending.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'APPROVAL_PENDING', 'EMAIL',
     'notification.approval_pending.title', 'notification.approval_pending.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- APPROVAL_REJECTED
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'APPROVAL_REJECTED', 'IN_APP',
     'notification.approval_rejected.title', 'notification.approval_rejected.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'APPROVAL_REJECTED', 'EMAIL',
     'notification.approval_rejected.title', 'notification.approval_rejected.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- MIN_STOCK_ALERT
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'MIN_STOCK_ALERT', 'IN_APP',
     'notification.min_stock_alert.title', 'notification.min_stock_alert.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'MIN_STOCK_ALERT', 'EMAIL',
     'notification.min_stock_alert.title', 'notification.min_stock_alert.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- PO_DELIVERY_LATE
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'PO_DELIVERY_LATE', 'IN_APP',
     'notification.po_delivery_late.title', 'notification.po_delivery_late.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'PO_DELIVERY_LATE', 'EMAIL',
     'notification.po_delivery_late.title', 'notification.po_delivery_late.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- RFQ_DEADLINE_APPROACHING
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'RFQ_DEADLINE_APPROACHING', 'IN_APP',
     'notification.rfq_deadline_approaching.title', 'notification.rfq_deadline_approaching.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'RFQ_DEADLINE_APPROACHING', 'EMAIL',
     'notification.rfq_deadline_approaching.title', 'notification.rfq_deadline_approaching.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- RFQ_NO_RESPONSE
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'RFQ_NO_RESPONSE', 'IN_APP',
     'notification.rfq_no_response.title', 'notification.rfq_no_response.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), sys_tenant, 'RFQ_NO_RESPONSE', 'EMAIL',
     'notification.rfq_no_response.title', 'notification.rfq_no_response.body',
     'HIGH', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- =========================================================
  -- NORMAL events — sadece IN_APP (email opsiyonel)
  -- =========================================================

  -- WORK_ORDER_APPROVED
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'WORK_ORDER_APPROVED', 'IN_APP',
     'notification.work_order_approved.title', 'notification.work_order_approved.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- APPROVAL_APPROVED
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'APPROVAL_APPROVED', 'IN_APP',
     'notification.approval_approved.title', 'notification.approval_approved.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- BATCH_QC_PENDING
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'BATCH_QC_PENDING', 'IN_APP',
     'notification.batch_qc_pending.title', 'notification.batch_qc_pending.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- GOODS_RECEIPT_CONFIRMED
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'GOODS_RECEIPT_CONFIRMED', 'IN_APP',
     'notification.goods_receipt_confirmed.title', 'notification.goods_receipt_confirmed.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- PO_CONFIRMED
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'PO_CONFIRMED', 'IN_APP',
     'notification.po_confirmed.title', 'notification.po_confirmed.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- PO_PARTIALLY_RECEIVED
  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant, 'PO_PARTIALLY_RECEIVED', 'IN_APP',
     'notification.po_partially_received.title', 'notification.po_partially_received.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

END $$;
