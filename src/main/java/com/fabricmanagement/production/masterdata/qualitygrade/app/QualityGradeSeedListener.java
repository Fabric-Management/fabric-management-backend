package com.fabricmanagement.production.masterdata.qualitygrade.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.event.TenantCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Seeds default {@link com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade}
 * records when a new tenant is created.
 *
 * <p>Runs asynchronously after the tenant creation transaction commits to avoid extending the
 * tenant onboarding transaction. Failure here does not roll back the tenant creation — grades can
 * be seeded manually via admin API if needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityGradeSeedListener {

  private final QualityGradeService qualityGradeService;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onTenantCreated(TenantCreatedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          try {
            idempotentHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onTenantCreated",
                () -> {
                  log.info(
                      "Seeding default quality grades for new tenant: tenantId={}, name={}",
                      event.getTenantId(),
                      event.getName());
                  qualityGradeService.seedDefaultGrades(event.getTenantId());
                });
          } catch (Exception e) {
            // Log but do not rethrow — seed failure must not fail tenant onboarding.
            log.error(
                "Failed to seed quality grades for tenantId={}. Manual seeding may be needed.",
                event.getTenantId(),
                e);
          }
          return null;
        });
  }
}
