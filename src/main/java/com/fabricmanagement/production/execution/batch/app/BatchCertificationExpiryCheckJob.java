package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job: finds active batch certifications that are expired or expiring within the
 * configured threshold (default 30 days) and notifies tenant users (e.g. ADMIN, MANAGER) via in-app
 * notification so they appear in dashboard / badge.
 *
 * <p>Runs nightly; tenant filter is mandatory (each tenant's data is queried and notified
 * separately).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchCertificationExpiryCheckJob {

  @Value("${application.batch-certification.expiry-warning-days:30}")
  private int expiryWarningDays;

  private final TenantRepository tenantRepository;
  private final BatchCertificationRepository batchCertificationRepository;
  private final InAppNotificationService inAppNotificationService;

  /**
   * Runs every night at 2 AM by default; override with
   * application.batch-certification.expiry-check-cron
   */
  @Scheduled(cron = "${application.batch-certification.expiry-check-cron:0 0 2 * * ?}")
  @Transactional(readOnly = true)
  public void checkExpiringCertifications() {
    LocalDate threshold = LocalDate.now().plusDays(expiryWarningDays);
    List<Tenant> tenants = tenantRepository.findAllActive();

    for (Tenant tenant : tenants) {
      UUID tenantId = tenant.getId();
      try {
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              List<BatchCertification> expiring =
                  batchCertificationRepository.findByTenantIdAndIsActiveTrueAndValidUntilBeforeOrOn(
                      tenantId, threshold);
              if (expiring.isEmpty()) {
                log.debug(
                    "Batch certification expiry check: tenant {} has no expiring certs.", tenantId);
                return null;
              }
              notifyTenantOfExpiringCerts(tenantId, expiring, threshold);
              log.info(
                  "Batch certification expiry: tenant {}, {} cert(s) expired or expiring by {}",
                  tenantId,
                  expiring.size(),
                  threshold);
              return null;
            });
      } catch (Exception e) {
        log.warn(
            "Batch certification expiry check failed for tenant {}: {}",
            tenantId,
            e.getMessage(),
            e);
      }
    }
  }

  private void notifyTenantOfExpiringCerts(
      UUID tenantId, List<BatchCertification> expiring, LocalDate threshold) {
    int count = expiring.size();
    String title = "Batch certification(s) expiring";
    String message =
        count == 1
            ? "1 batch certification is expired or expiring within "
                + expiryWarningDays
                + " days (by "
                + threshold
                + "). Check batch certifications."
            : count
                + " batch certifications are expired or expiring within "
                + expiryWarningDays
                + " days (by "
                + threshold
                + "). Check batch certifications.";

    inAppNotificationService.sendToTenantRoles(
        tenantId,
        InAppNotificationService.QUARANTINE_NOTIFY_ROLES,
        NotificationType.BATCH_CERTIFICATION_EXPIRING,
        title,
        message,
        null,
        "BATCH_CERTIFICATION_EXPIRING",
        NotificationDeliveryChannel.IN_APP);
  }
}
