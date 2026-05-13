package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderOutput;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderOutputRepository extends JpaRepository<WorkOrderOutput, UUID> {

  List<WorkOrderOutput> findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID workOrderId);

  boolean existsByTenantIdAndStockUnitIdAndIsActiveTrue(UUID tenantId, UUID stockUnitId);

  /** Paginated listing — outputs for a given work order. Sort via Pageable. */
  Page<WorkOrderOutput> findByTenantIdAndWorkOrderIdAndIsActiveTrue(
      UUID tenantId, UUID workOrderId, Pageable pageable);

  @Query(
      """
      SELECT COALESCE(SUM(o.outputWeight), 0)
      FROM WorkOrderOutput o
      WHERE o.tenantId = :tenantId
        AND o.workOrderId = :workOrderId
        AND o.isActive = true
      """)
  BigDecimal sumOutputWeightByWorkOrderId(
      @Param("tenantId") UUID tenantId, @Param("workOrderId") UUID workOrderId);

  /**
   * Returns per-productType aggregated output stats for a WorkOrder. DB does the GROUP BY — no
   * in-memory aggregation needed.
   */
  @Query(
      """
      SELECT o.productType        AS productType,
             SUM(o.outputWeight)   AS totalWeight,
             COUNT(o.id)           AS recordCount
      FROM WorkOrderOutput o
      WHERE o.tenantId    = :tenantId
        AND o.workOrderId = :workOrderId
        AND o.isActive    = true
      GROUP BY o.productType
      """)
  List<ProductTypeAggregation> aggregateByProductType(
      @Param("tenantId") UUID tenantId, @Param("workOrderId") UUID workOrderId);

  /** Projection for aggregated output per product type. */
  interface ProductTypeAggregation {
    ProductType getProductType();

    BigDecimal getTotalWeight();

    Long getRecordCount();
  }
}
