package com.fabricmanagement.notification.hub.domain;

/**
 * Bildirim sistemi event type sabitleri.
 *
 * <p>Tüm event type string'leri burada tanımlanır — listener'lar ve seed migration'lar arasında
 * tutarlılık sağlanır. Typo riski azalır.
 *
 * <p>Naming convention: Event sınıf adının UPPER_SNAKE_CASE hali. Örn: BatchQcFailedEvent →
 * BATCH_QC_FAILED
 */
public final class NotificationEventType {

  private NotificationEventType() {
    // constants only
  }

  // ---- CRITICAL ----
  public static final String BATCH_QC_FAILED = "BATCH_QC_FAILED";
  public static final String RETURN_RATE_EXCEEDED = "RETURN_RATE_EXCEEDED";

  // ---- HIGH ----
  public static final String WORK_ORDER_PENDING_APPROVAL = "WORK_ORDER_PENDING_APPROVAL";
  public static final String APPROVAL_PENDING = "APPROVAL_PENDING";
  public static final String APPROVAL_REJECTED = "APPROVAL_REJECTED";
  public static final String MIN_STOCK_ALERT = "MIN_STOCK_ALERT";
  public static final String PO_DELIVERY_LATE = "PO_DELIVERY_LATE";
  public static final String RFQ_DEADLINE_APPROACHING = "RFQ_DEADLINE_APPROACHING";
  public static final String RFQ_NO_RESPONSE = "RFQ_NO_RESPONSE";

  // ---- NORMAL ----
  public static final String WORK_ORDER_APPROVED = "WORK_ORDER_APPROVED";
  public static final String WORK_ORDER_DEADLINE_SET = "WORK_ORDER_DEADLINE_SET";
  public static final String BATCH_QC_PENDING = "BATCH_QC_PENDING";
  public static final String APPROVAL_APPROVED = "APPROVAL_APPROVED";
  public static final String GOODS_RECEIPT_CONFIRMED = "GOODS_RECEIPT_CONFIRMED";
  public static final String PO_CONFIRMED = "PO_CONFIRMED";
  public static final String PO_PARTIALLY_RECEIVED = "PO_PARTIALLY_RECEIVED";
  public static final String RFQ_SENT = "RFQ_SENT";
  public static final String SUPPLIER_QUOTE_RECEIVED = "SUPPLIER_QUOTE_RECEIVED";
  public static final String TASK_BLOCKED = "TASK_BLOCKED";
  public static final String AUTOMATION_ALERT = "AUTOMATION_ALERT";
}
