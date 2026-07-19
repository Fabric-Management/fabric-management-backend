package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntent;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchLotQuantityIntentRepository
    extends JpaRepository<BatchLotQuantityIntent, UUID> {

  List<BatchLotQuantityIntent> findByTenantIdAndQuoteLineId(UUID tenantId, UUID quoteLineId);

  Optional<BatchLotQuantityIntent> findByTenantIdAndQuoteLineIdAndBatchId(
      UUID tenantId, UUID quoteLineId, UUID batchId);

  List<BatchLotQuantityIntent> findByTenantIdAndStatusAndExpiresAtBeforeAndIsActiveTrue(
      UUID tenantId, BatchLotQuantityIntentStatus status, LocalDate expiresAt);

  List<BatchLotQuantityIntent> findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
      UUID tenantId, UUID quoteId, BatchLotQuantityIntentStatus status);

  @Query(
      """
      SELECT i.batchId AS batchId, COALESCE(SUM(i.quantity), 0) AS quantity
      FROM BatchLotQuantityIntent i
      WHERE i.tenantId = :tenantId
        AND i.batchId IN :batchIds
        AND i.status = :status
        AND i.isActive = true
        AND (:excludedQuoteLineId IS NULL OR i.quoteLineId <> :excludedQuoteLineId)
      GROUP BY i.batchId
      """)
  List<IntentQuantityRow> sumActiveRowsByBatchIds(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("status") BatchLotQuantityIntentStatus status,
      @Param("excludedQuoteLineId") UUID excludedQuoteLineId);

  @Query(
      """
      SELECT i.batchId AS batchId,
             UPPER(TRIM(i.unit)) AS unit,
             COALESCE(SUM(i.quantity), 0) AS quantity,
             COUNT(i.id) AS rowCount
      FROM BatchLotQuantityIntent i
      WHERE i.tenantId = :tenantId
        AND i.batchId IN :batchIds
        AND i.status = :status
        AND i.isActive = true
        AND (:excludedQuoteLineId IS NULL OR i.quoteLineId <> :excludedQuoteLineId)
      GROUP BY i.batchId, UPPER(TRIM(i.unit))
      """)
  List<IntentUnitQuantityRow> sumActiveRowsByBatchIdsAndUnit(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("status") BatchLotQuantityIntentStatus status,
      @Param("excludedQuoteLineId") UUID excludedQuoteLineId);

  @Query(
      """
      SELECT i FROM BatchLotQuantityIntent i
      WHERE i.tenantId = :tenantId
        AND i.batchId IN :batchIds
        AND i.status = :status
        AND i.isActive = true
        AND (:excludedQuoteLineId IS NULL OR i.quoteLineId <> :excludedQuoteLineId)
      ORDER BY i.expiresAt ASC, i.quoteNumber ASC
      """)
  List<BatchLotQuantityIntent> findActiveByBatchIds(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("status") BatchLotQuantityIntentStatus status,
      @Param("excludedQuoteLineId") UUID excludedQuoteLineId);

  default Map<UUID, BigDecimal> sumActiveByBatchIds(
      UUID tenantId, Collection<UUID> batchIds, UUID excludedQuoteLineId) {
    if (batchIds == null || batchIds.isEmpty()) {
      return Map.of();
    }
    return sumActiveRowsByBatchIds(
            tenantId, batchIds, BatchLotQuantityIntentStatus.ACTIVE, excludedQuoteLineId)
        .stream()
        .collect(Collectors.toMap(IntentQuantityRow::getBatchId, IntentQuantityRow::getQuantity));
  }

  default List<BatchLotQuantityIntent> findActiveByBatchIds(
      UUID tenantId, Collection<UUID> batchIds, UUID excludedQuoteLineId) {
    if (batchIds == null || batchIds.isEmpty()) {
      return List.of();
    }
    return findActiveByBatchIds(
        tenantId, batchIds, BatchLotQuantityIntentStatus.ACTIVE, excludedQuoteLineId);
  }

  interface IntentQuantityRow {
    UUID getBatchId();

    BigDecimal getQuantity();
  }

  interface IntentUnitQuantityRow {
    UUID getBatchId();

    String getUnit();

    BigDecimal getQuantity();

    long getRowCount();
  }
}
