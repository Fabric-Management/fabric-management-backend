package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.app.TenantNotificationDispatcher;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.notification.hub.domain.port.PlatformAdminRecipientPort;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.product.fiber.domain.event.FiberRequestApprovedEvent;
import com.fabricmanagement.product.fiber.domain.event.FiberRequestRejectedEvent;
import com.fabricmanagement.product.fiber.domain.event.FiberRequestSubmittedEvent;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Notifications between a tenant and the platform administrators (NOTIF-UNIFY-1 S2, B4).
 *
 * <p>A fibre request and a new company go to the platform administrators only, in the platform's
 * own tenant. The decision on a fibre request goes back to the requesting tenant: the person who
 * asked, and whoever there manages the fibre catalogue. Each is written in its target tenant
 * through {@link TenantNotificationDispatcher}, because these events are raised in the other one.
 */
@Component
@RequiredArgsConstructor
public class PlatformNotificationListener {

  static final String FIBER_REQUEST = "FIBER_REQUEST";

  private final TenantNotificationDispatcher dispatcher;
  private final PlatformAdminRecipientPort platformAdminRecipientPort;
  private final PermissionRecipientPort permissionRecipientPort;
  private final IdempotentEventHandler idempotentEventHandler;

  @ApplicationModuleListener
  public void onFiberRequestSubmitted(FiberRequestSubmittedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onFiberRequestSubmitted",
        () ->
            dispatcher.notifyInTenant(
                TenantContext.SYSTEM_TENANT_ID,
                platformAdminRecipientPort::findPlatformAdministrators,
                NotificationEventType.FIBER_REQUEST_SUBMITTED,
                Map.of(
                    "tenantName", nonNull(event.getTenantName()),
                    "fiberName", nonNull(event.getFiberName()),
                    "isoCode", nonNull(event.getIsoCode())),
                event.getRequestId(),
                FIBER_REQUEST));
  }

  @ApplicationModuleListener
  public void onFiberRequestApproved(FiberRequestApprovedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onFiberRequestApproved",
        () ->
            dispatcher.notifyInTenant(
                event.getTenantId(),
                () -> requesterAndCatalogueManagers(event.getTenantId(), event.getRequestedBy()),
                NotificationEventType.FIBER_REQUEST_APPROVED,
                Map.of(
                    "fiberName", nonNull(event.getFiberName()),
                    "isoCode", nonNull(event.getIsoCode())),
                event.getRequestId(),
                FIBER_REQUEST));
  }

  @ApplicationModuleListener
  public void onFiberRequestRejected(FiberRequestRejectedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onFiberRequestRejected",
        () ->
            dispatcher.notifyInTenant(
                event.getTenantId(),
                () -> requesterAndCatalogueManagers(event.getTenantId(), event.getRequestedBy()),
                NotificationEventType.FIBER_REQUEST_REJECTED,
                Map.of(
                    "fiberName", nonNull(event.getFiberName()),
                    "isoCode", nonNull(event.getIsoCode()),
                    "reviewNote", nonNull(event.getReviewNote())),
                event.getRequestId(),
                FIBER_REQUEST));
  }

  /**
   * A company joined by signing up. {@code plan} is what it signed up for: {@code TRIAL}, {@code
   * PLAYGROUND} (the demo), or {@code SALES_LED} when it asked to talk to sales first.
   */
  @ApplicationModuleListener
  public void onSelfSignupCompleted(SelfSignupCompletedEvent event) {
    idempotentEventHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onSelfSignupCompleted",
        () ->
            dispatcher.notifyInTenant(
                TenantContext.SYSTEM_TENANT_ID,
                platformAdminRecipientPort::findPlatformAdministrators,
                NotificationEventType.NEW_TENANT_ONBOARDED,
                Map.of(
                    "tenantName", nonNull(event.getOrganizationName()),
                    "plan", plan(event)),
                event.getTrialTenantId(),
                "TENANT"));
  }

  static String plan(SelfSignupCompletedEvent event) {
    if (event.isSalesLed()) {
      return "SALES_LED";
    }
    return "PLAYGROUND".equalsIgnoreCase(event.getSignupIntent()) ? "PLAYGROUND" : "TRIAL";
  }

  private Set<UUID> requesterAndCatalogueManagers(UUID tenantId, UUID requestedBy) {
    Set<UUID> recipients = new LinkedHashSet<>();
    if (requestedBy != null) {
      recipients.add(requestedBy);
    }
    recipients.addAll(
        permissionRecipientPort.findUsersWithPermission(tenantId, PermissionKey.FIBER_WRITE));
    return recipients;
  }

  private static String nonNull(String value) {
    return value != null ? value : "";
  }
}
