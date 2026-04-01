package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
}
