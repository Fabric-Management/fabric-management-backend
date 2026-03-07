package com.fabricmanagement.production.execution.lineage.infra.repository;

import com.fabricmanagement.production.execution.lineage.domain.BatchLineage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchLineageRepository extends JpaRepository<BatchLineage, UUID> {

  /** All lineage records for a tenant. */
  List<BatchLineage> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /** Forward trace: what batches were consumed to produce this child? */
  List<BatchLineage> findByTenantIdAndChildBatchIdAndIsActiveTrue(UUID tenantId, UUID childBatchId);

  /** Backward trace: where was this parent batch used? */
  List<BatchLineage> findByTenantIdAndParentBatchIdAndIsActiveTrue(
      UUID tenantId, UUID parentBatchId);

  /** Check if a specific parent→child link already exists. */
  boolean existsByParentBatchIdAndChildBatchId(UUID parentBatchId, UUID childBatchId);

  Optional<BatchLineage> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Sum of consumption percentages already recorded for a given child batch. Used to validate that
   * total does not exceed 100%.
   */
  @Query(
      "SELECT COALESCE(SUM(bl.consumptionPercentage), 0) "
          + "FROM BatchLineage bl "
          + "WHERE bl.childBatchId = :childBatchId "
          + "AND bl.isActive = true")
  java.math.BigDecimal sumConsumptionPercentageByChildBatchId(
      @Param("childBatchId") UUID childBatchId);
}
