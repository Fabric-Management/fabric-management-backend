package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link CostCalculation}. */
public interface CostCalculationRepository extends JpaRepository<CostCalculation, UUID> {

  /** Find the current active calculation for a specific entity+stage (excludes soft-deleted). */
  @Query(
      """
      SELECT cc FROM CostCalculation cc
      WHERE cc.entityType = :entityType
        AND cc.entityId = :entityId
        AND cc.stage = :stage
        AND cc.isActive = true
      """)
  Optional<CostCalculation> findActiveByEntityTypeAndEntityIdAndStage(
      @Param("entityType") CostEntityType entityType,
      @Param("entityId") UUID entityId,
      @Param("stage") CostStage stage);

  /** Find calculations for a specific entity+stage (e.g. all PLANNED costs for a WorkOrder). */
  Optional<CostCalculation> findByEntityTypeAndEntityIdAndStage(
      CostEntityType entityType, UUID entityId, CostStage stage);

  /** All cost calculations for an entity across all stages (for variance report). */
  @Query(
      """
      SELECT cc FROM CostCalculation cc
      WHERE cc.entityType = :entityType AND cc.entityId = :entityId
      ORDER BY cc.stage
      """)
  List<CostCalculation> findAllStagesForEntity(
      @Param("entityType") CostEntityType entityType, @Param("entityId") UUID entityId);

  List<CostCalculation> findByTenantIdAndEntityTypeAndStage(
      UUID tenantId,
      CostEntityType entityType,
      CostStage stage,
      org.springframework.data.domain.Pageable pageable);

  @Query(
      """
      SELECT cc FROM CostCalculation cc
      WHERE cc.tenantId = :tenantId
        AND cc.entityType = :entityType
        AND cc.stage = :stage
        AND cc.entityId IN :entityIds
        AND cc.isActive = true
      """)
  List<CostCalculation> findActiveByTenantIdAndEntityTypeAndStageAndEntityIdIn(
      @Param("tenantId") UUID tenantId,
      @Param("entityType") CostEntityType entityType,
      @Param("stage") CostStage stage,
      @Param("entityIds") java.util.Set<UUID> entityIds);
}
