package com.fabricmanagement.common.platform.organization.app;

import com.fabricmanagement.common.platform.audit.app.AuditService;
import com.fabricmanagement.common.platform.organization.domain.event.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Event listener for organization domain events.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Audit logging for organization lifecycle events
 *   <li>Future: Analytics, department sync, ERP integration
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationEventListener {

  private final AuditService auditService;

  /**
   * Handle organization created event.
   *
   * <p>Logs the event for audit purposes. Future integrations:
   *
   * <ul>
   *   <li>Default department seeding trigger
   *   <li>ERP sync for organizational hierarchy
   *   <li>Analytics tracking
   * </ul>
   */
  @EventListener
  public void onOrganizationCreated(OrganizationCreatedEvent event) {
    log.info(
        "[AUDIT] Organization created: tenantId={}, organizationId={}, name={}, type={}",
        event.getTenantId(),
        event.getOrganizationId(),
        event.getName(),
        event.getOrganizationType());

    auditService.logAction(
        "ORGANIZATION_CREATED",
        "organization",
        event.getOrganizationId().toString(),
        String.format(
            "Organization created: name=%s, type=%s",
            event.getName(), event.getOrganizationType()));
  }
}
