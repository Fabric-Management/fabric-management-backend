package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.production.core.batch.domain.event.BatchCertificationsExpiringEvent;
import com.fabricmanagement.production.core.batch.infra.repository.BatchCertificationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled job: finds active batch certifications that are expired or expiring within the
 * configured threshold (default 30 days) and tells each tenant, through the notification hub, how
 * many need renewing ({@link BatchCertificationsExpiringEvent}).
 *
 * <p>Runs nightly. Each tenant is checked in its own context and its own transaction: the session
 * of a transaction is bound to the tenant it started in, and the event must be published inside a
 * writable transaction for the hub's listener to receive it after commit.
 */
@Component
@Slf4j
public class BatchCertificationExpiryCheckJob {

  @Value("${application.batch-certification.expiry-warning-days:30}")
  private int expiryWarningDays;

  private final TenantQueryPort tenantQueryPort;
  private final BatchCertificationRepository batchCertificationRepository;
  private final DomainEventPublisher eventPublisher;
  private final TransactionTemplate tenantTransaction;

  public BatchCertificationExpiryCheckJob(
      TenantQueryPort tenantQueryPort,
      BatchCertificationRepository batchCertificationRepository,
      DomainEventPublisher eventPublisher,
      PlatformTransactionManager transactionManager) {
    this.tenantQueryPort = tenantQueryPort;
    this.batchCertificationRepository = batchCertificationRepository;
    this.eventPublisher = eventPublisher;
    this.tenantTransaction = new TransactionTemplate(transactionManager);
    this.tenantTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Runs every night at 2 AM by default; override with
   * application.batch-certification.expiry-check-cron
   */
  @Scheduled(cron = "${application.batch-certification.expiry-check-cron:0 0 2 * * ?}")
  public void checkExpiringCertifications() {
    LocalDate threshold = LocalDate.now().plusDays(expiryWarningDays);
    List<TenantReference> tenants = tenantQueryPort.findAllActiveTenants();

    for (TenantReference tenant : tenants) {
      UUID tenantId = tenant.id();
      try {
        TenantContext.executeInTenantContext(
            tenantId,
            () ->
                tenantTransaction.executeWithoutResult(status -> checkTenant(tenantId, threshold)));
      } catch (Exception e) {
        log.warn(
            "Batch certification expiry check failed for tenant {}: {}",
            tenantId,
            e.getMessage(),
            e);
      }
    }
  }

  private void checkTenant(UUID tenantId, LocalDate threshold) {
    int count =
        batchCertificationRepository
            .findByTenantIdAndIsActiveTrueAndValidUntilBeforeOrOn(tenantId, threshold)
            .size();
    if (count == 0) {
      log.debug("Batch certification expiry check: tenant {} has no expiring certs.", tenantId);
      return;
    }
    eventPublisher.publish(new BatchCertificationsExpiringEvent(tenantId, count, threshold));
    log.info(
        "Batch certification expiry: tenant {}, {} cert(s) expired or expiring by {}",
        tenantId,
        count,
        threshold);
  }
}
