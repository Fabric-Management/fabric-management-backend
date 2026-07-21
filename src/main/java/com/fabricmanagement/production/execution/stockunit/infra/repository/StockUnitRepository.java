package com.fabricmanagement.production.execution.stockunit.infra.repository;

import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  List<StockUnit> findByTenantIdAndBatchIdInAndIsActiveTrue(UUID tenantId, List<UUID> batchIds);

  @Query(
      """
      SELECT DISTINCT s.batchId
      FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId IN :batchIds
        AND s.isActive = true
      """)
  Set<UUID> findBatchIdsWithActiveStockUnits(
      @Param("tenantId") UUID tenantId, @Param("batchIds") Collection<UUID> batchIds);

  Page<StockUnit> findByTenantIdAndBatchIdAndIsActiveTrue(
      UUID tenantId, UUID batchId, Pageable pageable);

  Page<StockUnit> findByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
      UUID tenantId, UUID batchId, Collection<StockUnitStatus> statuses, Pageable pageable);

  @Query(
      value =
          """
          SELECT b.id AS batchId,
                 b.batch_code AS batchCode,
                 b.product_id AS productId,
                 p.uid AS productUid,
                 b.product_type AS productType,
                 COALESCE(f.fiber_name, p.uid) AS productDisplayName,
                 c.id AS colorId,
                 c.name AS colorName,
                 b.supplier_batch_code AS supplierBatchCode,
                 COUNT(s.id) AS pendingUnitCount,
                 b.created_at AS batchCreatedAt
          FROM production.stock_unit s
          JOIN production.production_execution_batch b
            ON b.id = s.batch_id AND b.tenant_id = s.tenant_id
          JOIN production.prod_product p
            ON p.id = b.product_id
          LEFT JOIN production.prod_fiber f
            ON f.product_id = p.id
          LEFT JOIN production.color c
            ON c.id = b.color_id AND c.tenant_id = b.tenant_id
          WHERE s.tenant_id = :tenantId
            AND s.is_active = TRUE
            AND b.is_active = TRUE
            AND s.quality_disposition = 'PENDING_INSPECTION'
          GROUP BY b.id, b.batch_code, b.product_id, p.uid, b.product_type,
                   f.fiber_name, c.id, c.name, b.supplier_batch_code, b.created_at
          ORDER BY b.created_at ASC, b.id ASC
          """,
      countQuery =
          """
          SELECT COUNT(DISTINCT s.batch_id)
          FROM production.stock_unit s
          JOIN production.production_execution_batch b
            ON b.id = s.batch_id AND b.tenant_id = s.tenant_id
          WHERE s.tenant_id = :tenantId
            AND s.is_active = TRUE
            AND b.is_active = TRUE
            AND s.quality_disposition = 'PENDING_INSPECTION'
          """,
      nativeQuery = true)
  Page<QualityQueueRow> findQualityQueue(@Param("tenantId") UUID tenantId, Pageable pageable);

  long countByTenantIdAndBatchIdAndIsActiveTrue(UUID tenantId, UUID batchId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT s FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId = :batchId
        AND s.isActive = true
        AND s.status IN :statuses
      ORDER BY s.id
      """)
  List<StockUnit> lockDecisionPopulation(
      @Param("tenantId") UUID tenantId,
      @Param("batchId") UUID batchId,
      @Param("statuses") Collection<StockUnitStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT s FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId = :batchId
        AND s.id IN :ids
        AND s.isActive = true
        AND s.status IN :statuses
      ORDER BY s.id
      """)
  List<StockUnit> lockSelectedDecisionPopulation(
      @Param("tenantId") UUID tenantId,
      @Param("batchId") UUID batchId,
      @Param("ids") Collection<UUID> ids,
      @Param("statuses") Collection<StockUnitStatus> statuses);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      UPDATE StockUnit s
      SET s.qualityDisposition = :disposition,
          s.version = s.version + 1
      WHERE s.tenantId = :tenantId
        AND s.batchId = :batchId
        AND s.id IN :ids
        AND s.isActive = true
        AND s.status IN :statuses
      """)
  int applyQualityDisposition(
      @Param("tenantId") UUID tenantId,
      @Param("batchId") UUID batchId,
      @Param("ids") Collection<UUID> ids,
      @Param("statuses") Collection<StockUnitStatus> statuses,
      @Param("disposition")
          com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition
              disposition);

  @Query(
      """
      SELECT s.qualityDisposition AS disposition, COUNT(s.id) AS unitCount
      FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId = :batchId
        AND s.isActive = true
      GROUP BY s.qualityDisposition
      """)
  List<QualityDispositionCount> countQualityDispositions(
      @Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);

  @Query(
      """
      SELECT s.batchId AS batchId,
             s.status AS status,
             s.qualityGradeId AS qualityGradeId,
             UPPER(TRIM(s.unit)) AS weightUnit,
             COALESCE(SUM(s.currentWeight), 0) AS weightQuantity,
             UPPER(TRIM(s.lengthUnit)) AS lengthUnit,
             SUM(s.length) AS lengthQuantity,
             COUNT(s.id) AS pieceCount
      FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId IN :batchIds
        AND s.isActive = true
        AND s.qualityDisposition = com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition.RELEASED
      GROUP BY s.batchId, s.status, s.qualityGradeId,
               UPPER(TRIM(s.unit)), UPPER(TRIM(s.lengthUnit))
      """)
  List<AvailabilityVectorRow> findAvailabilityVectorRows(
      @Param("tenantId") UUID tenantId, @Param("batchIds") Collection<UUID> batchIds);

  @Query(
      """
      SELECT s.batchId AS batchId,
             s.packageType AS packageType,
             UPPER(TRIM(s.unit)) AS weightUnit,
             COALESCE(SUM(s.currentWeight), 0) AS weightQuantity,
             UPPER(TRIM(s.lengthUnit)) AS lengthUnit,
             SUM(s.length) AS lengthQuantity,
             COUNT(s.id) AS pieceCount
      FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId IN :batchIds
        AND s.isActive = true
        AND s.status IN :statuses
        AND s.qualityDisposition = com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition.RELEASED
        AND (:qualityGradeId IS NULL OR s.qualityGradeId = :qualityGradeId)
        AND (:qualityUnassigned = false OR s.qualityGradeId IS NULL)
      GROUP BY s.batchId, s.packageType,
               UPPER(TRIM(s.unit)), UPPER(TRIM(s.lengthUnit))
      """)
  List<AvailabilityPieceBreakdownRow> findAvailabilityPieceBreakdownRows(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("statuses") Collection<StockUnitStatus> statuses,
      @Param("qualityGradeId") UUID qualityGradeId,
      @Param("qualityUnassigned") boolean qualityUnassigned);

  @Query(
      """
      SELECT s.batchId AS batchId,
             s.qualityGradeId AS qualityGradeId,
             UPPER(TRIM(s.unit)) AS weightUnit,
             COALESCE(SUM(s.currentWeight), 0) AS weightQuantity,
             UPPER(TRIM(s.lengthUnit)) AS lengthUnit,
             SUM(s.length) AS lengthQuantity,
             COUNT(s.id) AS pieceCount
      FROM StockUnit s
      WHERE s.tenantId = :tenantId
        AND s.batchId IN :batchIds
        AND s.isActive = true
        AND s.status IN :statuses
        AND s.qualityDisposition = com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition.RELEASED
        AND (:qualityGradeId IS NULL OR s.qualityGradeId = :qualityGradeId)
        AND (:qualityUnassigned = false OR s.qualityGradeId IS NULL)
      GROUP BY s.batchId, s.qualityGradeId,
               UPPER(TRIM(s.unit)), UPPER(TRIM(s.lengthUnit))
      """)
  List<AvailabilityQualityBreakdownRow> findAvailabilityQualityBreakdownRows(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("statuses") Collection<StockUnitStatus> statuses,
      @Param("qualityGradeId") UUID qualityGradeId,
      @Param("qualityUnassigned") boolean qualityUnassigned);

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

  interface AvailabilityVectorRow {
    UUID getBatchId();

    StockUnitStatus getStatus();

    UUID getQualityGradeId();

    String getWeightUnit();

    BigDecimal getWeightQuantity();

    String getLengthUnit();

    BigDecimal getLengthQuantity();

    long getPieceCount();
  }

  interface AvailabilityPieceBreakdownRow {
    UUID getBatchId();

    com.fabricmanagement.production.execution.stockunit.domain.PackageType getPackageType();

    String getWeightUnit();

    BigDecimal getWeightQuantity();

    String getLengthUnit();

    BigDecimal getLengthQuantity();

    long getPieceCount();
  }

  interface AvailabilityQualityBreakdownRow {
    UUID getBatchId();

    UUID getQualityGradeId();

    String getWeightUnit();

    BigDecimal getWeightQuantity();

    String getLengthUnit();

    BigDecimal getLengthQuantity();

    long getPieceCount();
  }

  interface QualityDispositionCount {
    com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition getDisposition();

    long getUnitCount();
  }

  interface QualityQueueRow {
    UUID getBatchId();

    String getBatchCode();

    UUID getProductId();

    String getProductUid();

    String getProductType();

    String getProductDisplayName();

    UUID getColorId();

    String getColorName();

    String getSupplierBatchCode();

    long getPendingUnitCount();

    java.time.Instant getBatchCreatedAt();
  }
}
