package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
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
public interface WorkOrderConsumptionRepository extends JpaRepository<WorkOrderConsumption, UUID> {

  List<WorkOrderConsumption> findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID workOrderId);

  List<WorkOrderConsumption> findByTenantIdAndStockUnitIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID stockUnitId);

  /** Paginated listing — consumptions for a given work order. Sort via Pageable. */
  Page<WorkOrderConsumption> findByTenantIdAndWorkOrderIdAndIsActiveTrue(
      UUID tenantId, UUID workOrderId, Pageable pageable);

  @Query(
      """
      SELECT COALESCE(SUM(c.consumedWeight), 0)
      FROM WorkOrderConsumption c
      WHERE c.tenantId = :tenantId
        AND c.workOrderId = :workOrderId
        AND c.isActive = true
      """)
  BigDecimal sumConsumedWeightByWorkOrderId(
      @Param("tenantId") UUID tenantId, @Param("workOrderId") UUID workOrderId);

  /**
   * Returns per-materialType aggregated consumption stats for a WorkOrder. DB does the GROUP BY —
   * no in-memory aggregation needed.
   */
  @Query(
      """
      SELECT c.materialType        AS materialType,
             SUM(c.consumedWeight)  AS totalWeight,
             COUNT(c.id)            AS recordCount
      FROM WorkOrderConsumption c
      WHERE c.tenantId    = :tenantId
        AND c.workOrderId = :workOrderId
        AND c.isActive    = true
      GROUP BY c.materialType
      """)
  List<MaterialTypeAggregation> aggregateByMaterialType(
      @Param("tenantId") UUID tenantId, @Param("workOrderId") UUID workOrderId);

  /** Projection for aggregated consumption per material type. */
  interface MaterialTypeAggregation {
    MaterialType getMaterialType();

    BigDecimal getTotalWeight();

    Long getRecordCount();
  }
}
