package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionCommand;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionService;
import com.fabricmanagement.production.quality.decision.app.TrustedDecisionContext;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Converts fiber-test approvals into immutable quality decisions. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchQcEventListener {

  private final QualityDecisionService qualityDecisionService;
  private final InAppNotificationService notificationService;
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
                  handleQcResult(event);
                  return null;
                }));
  }

  private void handleQcResult(FiberTestResultApprovedEvent event) {
    if (event.getApprovalStatus() == TestApprovalStatus.PENDING) {
      return;
    }
    if (event.getApprovalStatus() == TestApprovalStatus.CONDITIONAL_ACCEPT) {
      notifyConcessionReview(event);
      return;
    }

    boolean approved = event.getApprovalStatus() == TestApprovalStatus.APPROVED;
    QualityDecisionScope scope =
        event.getStockUnitId() == null
            ? QualityDecisionScope.FULL_LOT
            : QualityDecisionScope.SELECTED_UNITS;
    Set<UUID> unitIds = event.getStockUnitId() == null ? Set.of() : Set.of(event.getStockUnitId());

    qualityDecisionService.recordDecision(
        TrustedDecisionContext.qcEvent(event.getActorId(), event.getEventId()),
        new QualityDecisionCommand(
            event.getBatchId(),
            scope,
            approved ? QualityDecisionOutcome.RELEASED : QualityDecisionOutcome.NONCONFORMING,
            approved ? QualityReasonCode.SYSTEM_QC_PASSED : QualityReasonCode.SYSTEM_QC_REJECTED,
            null,
            unitIds,
            null));
  }

  private void notifyConcessionReview(FiberTestResultApprovedEvent event) {
    notificationService.sendToTenantRoles(
        event.getTenantId(),
        InAppNotificationService.QUARANTINE_NOTIFY_ROLES,
        NotificationType.BATCH_QUARANTINE,
        "Concession review required",
        "Conditional QC acceptance requires a scoped concession review",
        event.getBatchId(),
        "BATCH",
        NotificationDeliveryChannel.IN_APP);
    log.info("Conditional QC result left pending: batchId={}", event.getBatchId());
  }
}
