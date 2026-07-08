package com.fabricmanagement.production.execution.stockunit.infra.repository;

import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSoftHoldStatus;
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

  interface SoftHoldCountRow {
    UUID getStockUnitId();

    Long getHoldCount();
  }
}
