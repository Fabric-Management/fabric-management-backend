package com.fabricmanagement.notification.hub.domain;

import static com.fabricmanagement.notification.hub.domain.NotificationGroup.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every notification the hub can send, and the preference group each belongs to.
 *
 * <p>The constant's name is the event type string stored in templates, queue rows and the log, and
 * it is the vocabulary the API publishes: clients receive it as an enum instead of copying a list.
 * {@link NotificationEventType} keeps the same names as plain strings for the listeners; a test
 * keeps the two in step, and another fails when the golden tenant has a template for an event this
 * enum does not know.
 */
public enum NotificationEvent {
  // Approvals
  APPROVAL_PENDING(APPROVALS),
  APPROVAL_APPROVED(APPROVALS),
  APPROVAL_REJECTED(APPROVALS),
  WORK_ORDER_PENDING_APPROVAL(APPROVALS),
  FIBER_REQUEST_APPROVED(APPROVALS),
  FIBER_REQUEST_REJECTED(APPROVALS),

  // Sales
  CUSTOMER_OWNERSHIP_TRIAGE_OPENED(SALES),
  CUSTOMER_OWNERSHIP_TRIAGE_AGED(SALES),

  // Production
  WORK_ORDER_APPROVED(PRODUCTION),

  // Quality
  BATCH_QC_FAILED(QUALITY),
  BATCH_QC_PENDING(QUALITY),
  BATCH_CONCESSION_REVIEW(QUALITY),
  BATCH_NO_QUALITY_STANDARD(QUALITY),
  BATCH_CERTIFICATION_EXPIRING(QUALITY),

  // Procurement
  GOODS_RECEIPT_CONFIRMED(PROCUREMENT),
  PO_CONFIRMED(PROCUREMENT),
  PO_PARTIALLY_RECEIVED(PROCUREMENT),
  PO_DELIVERY_LATE(PROCUREMENT),
  RFQ_SENT(PROCUREMENT),
  RETURN_RATE_EXCEEDED(PROCUREMENT),
  RFQ_DEADLINE_APPROACHING(PROCUREMENT),
  RFQ_NO_RESPONSE(PROCUREMENT),
  SUPPLIER_QUOTE_RECEIVED(PROCUREMENT),

  // Finance
  INVOICE_OVERDUE(FINANCE),
  INVOICE_DISPUTED(FINANCE),
  PAYMENT_RECEIVED(FINANCE),
  COST_VARIANCE_DETECTED(FINANCE),

  // Inventory
  MIN_STOCK_ALERT(INVENTORY),

  // System
  ROUTING_FAILURE(SYSTEM),
  AUTOMATION_ALERT(SYSTEM),
  STUCK_FOLLOW_UP_RESOLVED(SYSTEM),

  // Platform administrators only (B4): delivered in the platform's own tenant
  FIBER_REQUEST_SUBMITTED(SYSTEM, true),
  NEW_TENANT_ONBOARDED(SYSTEM, true);

  private static final Map<String, NotificationEvent> BY_TYPE =
      Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));

  private final NotificationGroup group;
  private final boolean platformOnly;

  NotificationEvent(NotificationGroup group) {
    this(group, false);
  }

  NotificationEvent(NotificationGroup group, boolean platformOnly) {
    this.group = group;
    this.platformOnly = platformOnly;
  }

  public NotificationGroup group() {
    return group;
  }

  /**
   * Whether the event goes only to platform administrators (B4). Such an event is delivered in the
   * platform's own (system) tenant, so its template and wording live there and not in the golden
   * tenant that customer tenants are provisioned from.
   */
  public boolean platformOnly() {
    return platformOnly;
  }

  /** The event for a stored event type string, if the hub knows it. */
  public static Optional<NotificationEvent> find(String eventType) {
    return Optional.ofNullable(eventType).map(BY_TYPE::get);
  }
}
