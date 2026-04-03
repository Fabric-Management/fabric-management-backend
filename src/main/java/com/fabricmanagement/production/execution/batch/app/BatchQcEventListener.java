package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to {@link FiberTestResultApprovedEvent} and updates the associated batch status.
 *
 * <p>Mapping:
 *
 * <ul>
 *   <li>APPROVED → AVAILABLE (automatic release)
 *   <li>CONDITIONAL_ACCEPT → QUARANTINE (user decides next step)
 *   <li>REJECTED → QC_REJECTED (automatic block)
 *   <li>PENDING → no change
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchQcEventListener {

  private static final Set<BatchStatus> QC_AWAITING_STATUSES =
      Set.of(BatchStatus.PENDING_QC, BatchStatus.QUARANTINE);

  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final InAppNotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onFiberTestResultApproved(FiberTestResultApprovedEvent event) {
    if (event.getApprovalStatus() == TestApprovalStatus.PENDING) {
      return;
    }

    if (event.getStockUnitId() != null) {
      handleStockUnitQcResult(event);
    } else {
      handleBatchQcResult(event);
    }
  }

  private void handleStockUnitQcResult(FiberTestResultApprovedEvent event) {
    StockUnit su =
        stockUnitRepository
            .findByIdAndTenantIdAndIsActiveTrue(event.getStockUnitId(), event.getTenantId())
            .orElse(null);

    if (su == null) {
      log.warn(
          "StockUnit not found for QC event: stockUnitId={}, tenantId={}",
          event.getStockUnitId(),
          event.getTenantId());
      return;
    }

    switch (event.getApprovalStatus()) {
      case APPROVED -> {
        if (su.getStatus() == StockUnitStatus.QUARANTINE) {
          su.releaseQuarantine();
        } else if (su.getStatus() == StockUnitStatus.ON_HOLD) {
          su.releaseHold();
        }
      }
      case REJECTED -> {
        if (su.getStatus().canTransitionTo(StockUnitStatus.QUARANTINE)) {
          su.quarantine();
        }
      }
      case CONDITIONAL_ACCEPT -> {
        if (su.getStatus().canTransitionTo(StockUnitStatus.ON_HOLD)) {
          su.hold();
        }
      }
      case PENDING -> {}
    }

    stockUnitRepository.save(su);
    log.info(
        "StockUnit status updated by QC: stockUnitId={}, approval={}",
        event.getStockUnitId(),
        event.getApprovalStatus());
  }

  private void handleBatchQcResult(FiberTestResultApprovedEvent event) {
    BatchStatus targetStatus = mapApprovalToBatchStatus(event.getApprovalStatus());
    if (targetStatus == null) {
      return;
    }

    Batch batch =
        batchRepository.findByIdAndTenantId(event.getBatchId(), event.getTenantId()).orElse(null);

    if (batch == null) {
      log.warn(
          "Batch not found for QC event: batchId={}, tenantId={}",
          event.getBatchId(),
          event.getTenantId());
      return;
    }

    if (!QC_AWAITING_STATUSES.contains(batch.getStatus())) {
      log.debug(
          "Batch {} already in {} — skipping QC status update (approval={})",
          batch.getBatchCode(),
          batch.getStatus(),
          event.getApprovalStatus());
      return;
    }

    BatchStatus fromStatus = batch.getStatus();
    batch.transitionStatus(targetStatus, event.getActorId());
    batchRepository.save(batch);

    log.info(
        "Batch status updated by QC: batchId={}, {} → {} (approval={})",
        event.getBatchId(),
        fromStatus,
        targetStatus,
        event.getApprovalStatus());

    if (targetStatus == BatchStatus.QUARANTINE) {
      notificationService.sendToTenantRoles(
          event.getTenantId(),
          InAppNotificationService.QUARANTINE_NOTIFY_ROLES,
          NotificationType.BATCH_QUARANTINE,
          "Batch Quarantined",
          batch.getBatchCode() + " moved to quarantine — requires review",
          event.getBatchId(),
          "BATCH",
          NotificationDeliveryChannel.IN_APP);
      log.debug("BATCH_QUARANTINE notification sent: batchId={}", event.getBatchId());
    }
  }

  private static BatchStatus mapApprovalToBatchStatus(TestApprovalStatus approval) {
    return switch (approval) {
      case APPROVED -> BatchStatus.AVAILABLE;
      case CONDITIONAL_ACCEPT -> BatchStatus.QUARANTINE;
      case REJECTED -> BatchStatus.QC_REJECTED;
      case PENDING -> null;
    };
  }
}
