package com.fabricmanagement.production.execution.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Passive mode reconciliation cron job.
 *
 * <p>Runs nightly to detect discrepancies between the Batch lot-level summary and the physical
 * StockUnit weights. The comparison is:
 *
 * <pre>
 *   batch.quantity - batch.consumedQuantity  ==  SUM(stockUnit.currentWeight WHERE status NOT DISPOSED)
 * </pre>
 *
 * Both sides represent "total weight that has entered the system and has not yet been written off"
 * — reserved weight is still physically present and must be counted on both sides.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockUnitReconciliationService {

  private final TenantQueryPort tenantQueryPort;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;

  @Value("${application.stockunit.reconciliation-auto-fix:false}")
  private boolean autoFix;

  /** Run every night at 3 AM. */
  @Scheduled(cron = "${application.stockunit.reconciliation-cron:0 0 3 * * ?}")
  public void reconcileAllTenants() {
    List<TenantReference> tenants = tenantQueryPort.findAllActiveTenants();

    for (TenantReference tenant : tenants) {
      UUID tenantId = tenant.id();
      try {
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              reconcileTenantBatches(tenantId);
              return null;
            });
      } catch (Exception e) {
        log.error("Failed to reconcile StockUnits for tenant {}: {}", tenantId, e.getMessage(), e);
      }
    }
  }

  private void reconcileTenantBatches(UUID tenantId) {
    // Reconcile batches that are in progress or available
    List<Batch> activeBatches =
        batchRepository.findByTenantIdAndStatusIn(
            tenantId,
            List.of(BatchStatus.AVAILABLE, BatchStatus.RESERVED, BatchStatus.IN_PROGRESS));

    if (activeBatches == null || activeBatches.isEmpty()) {
      return;
    }

    int discrepancyCount = 0;

    for (Batch batch : activeBatches) {
      BigDecimal physicalSum =
          stockUnitRepository.sumCurrentWeightByBatchId(
              tenantId,
              batch.getId(),
              com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus.DISPOSED);
      if (physicalSum == null) {
        physicalSum = BigDecimal.ZERO;
      }

      // C3: Correct comparison:
      //   Batch side:        quantity - consumedQuantity  (physical stock not yet written off)
      //   StockUnit side:    SUM(currentWeight) excluding DISPOSED  (same concept)
      // Note: getAvailableQuantity() also subtracts reservedQuantity, which would create a
      // systematic false-positive for every batch with active reservations.
      BigDecimal nominalRemaining = batch.getQuantity().subtract(batch.getConsumedQuantity());

      // If the difference is non-zero, log it. In a real scenario we might have an acceptable
      // tolerance.
      if (physicalSum.compareTo(nominalRemaining) != 0) {
        log.warn(
            "Reconciliation discrepancy found! Tenant: {}, Batch: {}, NominalRemaining: {}, PhysicalSum: {}",
            tenantId,
            batch.getBatchCode(),
            nominalRemaining,
            physicalSum);

        if (autoFix) {
          BigDecimal correctedConsumed = batch.getQuantity().subtract(physicalSum);
          if (correctedConsumed.compareTo(BigDecimal.ZERO) < 0) {
            log.warn(
                "Cannot auto-fix Batch {}: corrected consumed would be negative ({})",
                batch.getBatchCode(),
                correctedConsumed);
          } else {
            BigDecimal oldConsumed = batch.getConsumedQuantity();
            batch.setConsumedQuantity(correctedConsumed);
            batchRepository.save(batch);
            log.info(
                "Auto-fixed Batch {}: updated consumedQuantity from {} to {}",
                batch.getBatchCode(),
                oldConsumed,
                correctedConsumed);
          }
        }

        discrepancyCount++;
      }
    }

    if (discrepancyCount > 0) {
      log.warn(
          "Reconciliation completed for tenant {} with {} discrepancies found.",
          tenantId,
          discrepancyCount);
    } else {
      log.debug("Reconciliation matched perfectly for tenant {}", tenantId);
    }
  }
}
