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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Passive mode reconciliation cron job. Runs periodically to compare the sum of current StockUnit
 * weights against the denormalized getAvailableQuantity() on the parent Batch. Logs discrepancies
 * for operational tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockUnitReconciliationService {

  private final TenantQueryPort tenantQueryPort;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;

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

      BigDecimal nominalAvailable = batch.getAvailableQuantity();

      // If the difference is non-zero, log it. In a real scenario we might have an acceptable
      // tolerance.
      if (physicalSum.compareTo(nominalAvailable) != 0) {
        log.warn(
            "Reconciliation discrepancy found! Tenant: {}, Batch: {}, NominalAvailable: {}, PhysicalSum: {}",
            tenantId,
            batch.getBatchCode(),
            nominalAvailable,
            physicalSum);
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
