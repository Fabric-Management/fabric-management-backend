package com.fabricmanagement.production.core.workorder.infra.repository;

import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only aggregates over completed work orders; the basis of history estimates (SOI A08). */
public interface WorkOrderHistoryRepository extends Repository<WorkOrder, UUID> {

  interface YieldRow {
    Long getSampleSize();

    Double getAvgYield();

    BigDecimal getMinYield();

    BigDecimal getMaxYield();

    Instant getLastCompletedAt();
  }

  interface LeadRow {
    Instant getCreatedAt();

    Instant getCompletedAt();
  }

  @Query(
      "SELECT COUNT(w) AS sampleSize, AVG(w.yieldPercentage) AS avgYield,"
          + " MIN(w.yieldPercentage) AS minYield, MAX(w.yieldPercentage) AS maxYield,"
          + " MAX(w.completedAt) AS lastCompletedAt FROM WorkOrder w"
          + " WHERE w.tenantId = :tenantId AND w.outputProductId = :productId"
          + " AND w.status ="
          + " com.fabricmanagement.production.core.workorder.domain.WorkOrderStatus.COMPLETED"
          + " AND w.yieldPercentage IS NOT NULL AND w.isActive = true")
  YieldRow yieldForProduct(@Param("tenantId") UUID tenantId, @Param("productId") UUID productId);

  @Query(
      "SELECT w.createdAt AS createdAt, w.completedAt AS completedAt FROM WorkOrder w"
          + " WHERE w.tenantId = :tenantId AND w.outputProductId = :productId"
          + " AND w.status ="
          + " com.fabricmanagement.production.core.workorder.domain.WorkOrderStatus.COMPLETED"
          + " AND w.completedAt IS NOT NULL AND w.isActive = true ORDER BY w.completedAt DESC")
  List<LeadRow> recentCompleted(
      @Param("tenantId") UUID tenantId, @Param("productId") UUID productId, Pageable page);
}
