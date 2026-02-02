package com.fabricmanagement.common.platform.tenant.app;

import com.fabricmanagement.common.platform.tenant.domain.event.TenantCreatedEvent;
import com.fabricmanagement.common.platform.tenant.domain.event.TenantStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Event listener for tenant domain events.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Audit logging for tenant lifecycle events
 *   <li>Future: Analytics, notifications, webhook triggers
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantEventListener {

  // TODO: Inject AuditService when available
  // private final AuditService auditService;

  /**
   * Handle tenant created event.
   *
   * <p>Logs the event for audit purposes. Future integrations:
   *
   * <ul>
   *   <li>Analytics tracking (new tenant signup)
   *   <li>Welcome notification to platform admin
   *   <li>Webhook trigger for CRM integration
   * </ul>
   */
  @EventListener
  public void onTenantCreated(TenantCreatedEvent event) {
    log.info(
        "[AUDIT] Tenant created: id={}, uid={}, name={}, status={}, trialEndsAt={}",
        event.getTenantId(),
        event.getUid(),
        event.getName(),
        event.getStatus(),
        event.getTrialEndsAt());

    // TODO: Save to audit table
    // auditService.logTenantCreation(event);
  }

  /**
   * Handle tenant status changed event.
   *
   * <p>Logs status transitions for audit. Critical for:
   *
   * <ul>
   *   <li>Trial → Active (subscription started)
   *   <li>Active → Suspended (payment issue)
   *   <li>Any → Terminated (account closed)
   * </ul>
   */
  @EventListener
  public void onTenantStatusChanged(TenantStatusChangedEvent event) {
    log.info(
        "[AUDIT] Tenant status changed: id={}, previousStatus={}, newStatus={}, reason={}",
        event.getTenantId(),
        event.getPreviousStatus(),
        event.getNewStatus(),
        event.getReason());

    // TODO: Trigger notifications based on status change
    // if (event.getNewStatus() == TenantStatus.SUSPENDED) {
    //   notificationService.notifyTenantSuspended(event);
    // }
  }
}
