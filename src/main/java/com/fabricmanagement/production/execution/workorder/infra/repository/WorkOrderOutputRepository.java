package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderOutput;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderOutputRepository extends JpaRepository<WorkOrderOutput, UUID> {

  List<WorkOrderOutput> findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID workOrderId);

  boolean existsByTenantIdAndStockUnitIdAndIsActiveTrue(UUID tenantId, UUID stockUnitId);

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
}
