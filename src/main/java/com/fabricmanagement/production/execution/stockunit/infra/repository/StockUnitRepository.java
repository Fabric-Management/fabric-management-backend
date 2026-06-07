package com.fabricmanagement.production.execution.stockunit.infra.repository;

import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Tenant-scoped persistence for {@link StockUnit}. */
@Repository
public interface StockUnitRepository extends JpaRepository<StockUnit, UUID> {

  Optional<StockUnit> findByTenantIdAndBarcode(UUID tenantId, String barcode);

  Optional<StockUnit> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);

  /**
   * Idempotency guard for event listeners. Returns true if at least one StockUnit already exists
   * for the given source record (e.g. GoodsReceiptItem). Used by {@code
   * GoodsReceiptConfirmedEventListener} to skip duplicate event processing.
   */
  boolean existsByTenantIdAndSourceTypeAndSourceId(
      UUID tenantId, StockUnitSourceType sourceType, UUID sourceId);

  List<StockUnit> findByTenantIdAndBatchIdAndStatusIn(
      UUID tenantId, UUID batchId, List<StockUnitStatus> statuses);

  Page<StockUnit> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

  boolean existsByTenantIdAndBatchIdAndIsActiveTrue(UUID tenantId, UUID batchId);

  boolean existsByTenantIdAndBatchIdInAndIsActiveTrue(UUID tenantId, List<UUID> batchIds);

  /**
   * Returns the sum of current weights for all non-disposed, active stock units of a batch. Used by
   * {@code StockUnitReconciliationService} to compare against {@code Batch.quantity}.
   *
   * <p>DISPOSED units are excluded because they are write-offs; the Batch quantity is reduced
   * separately via the disposal flow.
   *
   * @param excludedStatus pass {@code StockUnitStatus.DISPOSED} — parameterised for testability
   */
  @Query(
      """
      SELECT COALESCE(SUM(s.currentWeight), 0)
      FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId  = :batchId
        AND s.status  <> :excludedStatus
        AND s.isActive = true
      """)
  BigDecimal sumCurrentWeightByBatchId(
      @Param("tenantId") UUID tenantId,
      @Param("batchId") UUID batchId,
      @Param("excludedStatus") StockUnitStatus excludedStatus);
}
