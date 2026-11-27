package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.BatchConcessionReviewRequiredEvent;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Requests a concession review when a fibre test result receives conditional acceptance. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchQcEventListener {

  private final BatchRepository batchRepository;
  private final DomainEventPublisher eventPublisher;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onFiberTestResultApproved(FiberTestResultApprovedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onFiberTestResultApproved",
        () ->
            TenantContext.executeInTenantContext(
                event.getTenantId(),
                () -> {
                  if (event.getApprovalStatus() == TestApprovalStatus.CONDITIONAL_ACCEPT) {
                    notifyConcessionReview(event);
                  }
                  return null;
                }));
  }

  /** The people who decide on concessions hear of it through the notification hub. */
  private void notifyConcessionReview(FiberTestResultApprovedEvent event) {
    String batchCode =
        batchRepository
            .findById(event.getBatchId())
            .map(Batch::getBatchCode)
            .orElse(event.getBatchId().toString());
    eventPublisher.publish(
        new BatchConcessionReviewRequiredEvent(event.getTenantId(), event.getBatchId(), batchCode));
    log.info("Concession review requested: batchId={}", event.getBatchId());
  }
}
