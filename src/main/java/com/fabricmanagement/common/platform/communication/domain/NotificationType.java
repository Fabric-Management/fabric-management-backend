package com.fabricmanagement.common.platform.communication.domain;

/**
 * Notification type classification for in-app and email notifications.
 *
 * <p>Platform-level types target SYSTEM_TENANT_ID; tenant-level types target tenant users.
 */
public enum NotificationType {

  // ── Platform level (tenant_id = SYSTEM_TENANT_ID) ─────────────────────────

  /** New fiber request submitted by a tenant; platform admins are notified */
  FIBER_REQUEST_SUBMITTED,

  /** New tenant onboarded; platform admins are notified */
  NEW_TENANT_ONBOARDED,

  // ── Tenant level ─────────────────────────────────────────────────────────

  /** Fiber request approved; requester tenant is notified */
  FIBER_REQUEST_APPROVED,

  /** Fiber request rejected; requester tenant is notified */
  FIBER_REQUEST_REJECTED,

  /** Batch QC completed; relevant users are notified */
  BATCH_QC_COMPLETED,

  /** Batch quarantined; relevant users are notified */
  BATCH_QUARANTINE,

  /** Batch override required (e.g. quarantine override); relevant users are notified */
  BATCH_OVERRIDE_REQUIRED
}
