package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.production.core.batch.domain.event.BatchCertificationsExpiringEvent;
import com.fabricmanagement.production.quality.result.domain.event.BatchConcessionReviewRequiredEvent;
import com.fabricmanagement.production.quality.result.domain.event.BatchQualityStandardMissingEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Quality notifications that used to go straight to the legacy store for the hard-coded ADMIN and
 * MANAGER roles (NOTIF-UNIFY-1 S2). The tenant now decides who hears of them by granting the
 * permission that acts on them: {@code quality:approve} for a concession, {@code quality:manage}
 * for a missing quality standard and for expiring certificates.
 */
@Component
@RequiredArgsConstructor
public class QualityNotificationListener {

  private final NotificationHubService notificationHubService;
  private final PermissionRecipientPort permissionRecipientPort;
  private final IdempotentEventHandler idempotentEventHandler;

  @ApplicationModuleListener
  public void onBatchConcessionReviewRequired(BatchConcessionReviewRequiredEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentEventHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onBatchConcessionReviewRequired",
                () ->
                    notifyHolders(
                        event.getTenantId(),
                        PermissionKey.QUALITY_APPROVE,
                        NotificationEventType.BATCH_CONCESSION_REVIEW,
                        Map.of("batchCode", event.getBatchCode()),
                        event.getBatchId())));
  }

  @ApplicationModuleListener
  public void onBatchQualityStandardMissing(BatchQualityStandardMissingEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentEventHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onBatchQualityStandardMissing",
                () ->
                    notifyHolders(
                        event.getTenantId(),
                        PermissionKey.QUALITY_MANAGE,
                        NotificationEventType.BATCH_NO_QUALITY_STANDARD,
                        Map.of(
                            "batchCode", nonNull(event.getBatchCode()),
                            "target", nonNull(event.getTarget()),
                            "reason", nonNull(event.getReasonCode())),
                        event.getBatchId())));
  }

  @ApplicationModuleListener
  public void onBatchCertificationsExpiring(BatchCertificationsExpiringEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentEventHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onBatchCertificationsExpiring",
                () ->
                    notifyHolders(
                        event.getTenantId(),
                        PermissionKey.QUALITY_MANAGE,
                        NotificationEventType.BATCH_CERTIFICATION_EXPIRING,
                        Map.of(
                            "count", String.valueOf(event.getCount()),
                            "thresholdDate", event.getThresholdDate().toString()),
                        null)));
  }

  private void notifyHolders(
      UUID tenantId,
      PermissionKey permission,
      String eventType,
      Map<String, String> payload,
      UUID batchId) {
    List<UUID> recipients =
        permissionRecipientPort.findUsersWithPermission(tenantId, permission).stream()
            .sorted()
            .toList();
    if (recipients.isEmpty()) {
      return;
    }
    notificationHubService.notifyAll(
        recipients, tenantId, eventType, payload, batchId, batchId == null ? null : "BATCH");
  }

  private static String nonNull(String value) {
    return value != null ? value : "";
  }
}
