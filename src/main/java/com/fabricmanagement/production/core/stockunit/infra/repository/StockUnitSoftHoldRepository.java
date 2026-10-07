package com.fabricmanagement.production.core.stockunit.infra.repository;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHoldStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockUnitSoftHoldRepository extends JpaRepository<StockUnitSoftHold, UUID> {

  List<StockUnitSoftHold> findByTenantIdAndQuoteLineId(UUID tenantId, UUID quoteLineId);

  Optional<StockUnitSoftHold> findByTenantIdAndQuoteLineIdAndStockUnitId(
      UUID tenantId, UUID quoteLineId, UUID stockUnitId);

  @Query(
      """
      SELECT h.stockUnitId AS stockUnitId, COUNT(h.id) AS holdCount
      FROM StockUnitSoftHold h
      WHERE h.tenantId = :tenantId
        AND h.stockUnitId IN :stockUnitIds
        AND h.status = :status
        AND h.isActive = true
      GROUP BY h.stockUnitId
      """)
  List<SoftHoldCountRow> countActiveRowsByStockUnitIds(
      @Param("tenantId") UUID tenantId,
      @Param("stockUnitIds") Collection<UUID> stockUnitIds,
      @Param("status") StockUnitSoftHoldStatus status);

  default Map<UUID, Long> countActiveByStockUnitIds(UUID tenantId, Collection<UUID> stockUnitIds) {
    if (stockUnitIds == null || stockUnitIds.isEmpty()) {
      return Map.of();
    }
    return countActiveRowsByStockUnitIds(tenantId, stockUnitIds, StockUnitSoftHoldStatus.ACTIVE)
        .stream()
        .collect(
            Collectors.toMap(SoftHoldCountRow::getStockUnitId, SoftHoldCountRow::getHoldCount));
  }

  /**
   * Active piece holds on the stock units of the given lots, with the lot each piece belongs to
   * (STOCK-PREVIEW-1 A2). One query; tenant-scoped on both the hold and the piece.
   */
  @Query(
      """
      SELECT h.stockUnitId AS stockUnitId, s.batchId AS batchId, h.quoteLineId AS quoteLineId
      FROM StockUnitSoftHold h, StockUnit s
      WHERE h.tenantId = :tenantId
        AND s.tenantId = :tenantId
        AND s.id = h.stockUnitId
        AND s.batchId IN :batchIds
        AND s.isActive = true
        AND h.status = :status
        AND h.isActive = true
      ORDER BY s.batchId, h.stockUnitId, h.quoteLineId
      """)
  List<HeldPieceRow> findActiveRowsByBatchIds(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("status") StockUnitSoftHoldStatus status);

  default List<HeldPieceRow> findActiveByBatchIds(UUID tenantId, Collection<UUID> batchIds) {
    if (batchIds == null || batchIds.isEmpty()) {
      return List.of();
    }
    return findActiveRowsByBatchIds(tenantId, batchIds, StockUnitSoftHoldStatus.ACTIVE);
  }

  interface HeldPieceRow {
    UUID getStockUnitId();

    UUID getBatchId();

    UUID getQuoteLineId();
  }

  interface SoftHoldCountRow {
    UUID getStockUnitId();

    Long getHoldCount();
  }
}
