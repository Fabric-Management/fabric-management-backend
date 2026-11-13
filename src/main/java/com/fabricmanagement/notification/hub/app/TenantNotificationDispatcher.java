package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers a notification in a tenant other than the one the caller runs in.
 *
 * <p>A listener's transaction (and so its database session) is bound to the tenant it started in. A
 * platform-level notification is written in the platform's own tenant (B4), and a decision taken by
 * a platform administrator is written in the customer's tenant: neither can join the caller's
 * transaction, because row-level security admits only rows of the session's tenant. So the work
 * runs in the target tenant's context and in a transaction of its own, where the recipients are
 * looked up and the queue rows written.
 */
@Component
@RequiredArgsConstructor
public class TenantNotificationDispatcher {

  private final InTenantTransaction inTenantTransaction;

  /**
   * Notifies, in {@code tenantId}, every user {@code recipients} returns there.
   *
   * @param recipients evaluated inside the target tenant, where its users are visible
   */
  public void notifyInTenant(
      UUID tenantId,
      Supplier<? extends Collection<UUID>> recipients,
      String eventType,
      Map<String, String> payload,
      UUID referenceId,
      String referenceType) {
    TenantContext.executeInTenantContext(
        tenantId,
        () ->
            inTenantTransaction.notifyAll(
                tenantId, recipients, eventType, payload, referenceId, referenceType));
  }

  /** The transactional half; a separate bean so the new transaction starts in the target tenant. */
  @Component
  @RequiredArgsConstructor
  @Slf4j
  public static class InTenantTransaction {

    private final NotificationHubService hub;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyAll(
        UUID tenantId,
        Supplier<? extends Collection<UUID>> recipients,
        String eventType,
        Map<String, String> payload,
        UUID referenceId,
        String referenceType) {
      List<UUID> to = recipients.get().stream().distinct().sorted().toList();
      if (to.isEmpty()) {
        // Not an error, but a notification that reaches nobody is a gap worth seeing: a role
        // nobody holds, or a lookup that row-level security emptied.
        log.warn(
            "No recipient in tenant {} for {} (reference {} {}); nothing was queued",
            tenantId,
            eventType,
            referenceType,
            referenceId);
        return;
      }
      hub.notifyAll(to, tenantId, eventType, payload, referenceId, referenceType);
    }
  }
}
