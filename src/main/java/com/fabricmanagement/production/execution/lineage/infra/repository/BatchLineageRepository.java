package com.fabricmanagement.production.execution.lineage.infra.repository;

import com.fabricmanagement.production.execution.lineage.domain.BatchLineage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchLineageRepository extends JpaRepository<BatchLineage, UUID> {

  /** All lineage records for a tenant. */
  Page<BatchLineage> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

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

  /** Recursive CTE to fetch all ancestors (backward trace) up to a depth of 10. */
  @Query(
      value =
          """
      WITH RECURSIVE lineage_tree AS (
          SELECT bl.*, 1 as depth
          FROM production.production_execution_batch_lineage bl
          WHERE bl.child_batch_id = :batchId AND bl.tenant_id = :tenantId AND bl.is_active = true

          UNION ALL

          SELECT bl.*, lt.depth + 1
          FROM production.production_execution_batch_lineage bl
          INNER JOIN lineage_tree lt ON bl.child_batch_id = lt.parent_batch_id
          WHERE bl.tenant_id = :tenantId AND bl.is_active = true AND lt.depth < 10
      )
      SELECT id, tenant_id, uid, created_at, created_by, updated_at, updated_by, is_active, deleted_at, version,
             parent_batch_id, child_batch_id, consumed_quantity, unit, consumption_percentage,
             consumed_at, process_reference, remarks
      FROM lineage_tree
      """,
      nativeQuery = true)
  List<BatchLineage> findAncestorsWithDepthLimit(
      @Param("batchId") UUID batchId, @Param("tenantId") UUID tenantId);

  /** Recursive CTE to fetch all descendants (forward trace) up to a depth of 10. */
  @Query(
      value =
          """
      WITH RECURSIVE lineage_tree AS (
          SELECT bl.*, 1 as depth
          FROM production.production_execution_batch_lineage bl
          WHERE bl.parent_batch_id = :batchId AND bl.tenant_id = :tenantId AND bl.is_active = true

          UNION ALL

          SELECT bl.*, lt.depth + 1
          FROM production.production_execution_batch_lineage bl
          INNER JOIN lineage_tree lt ON bl.parent_batch_id = lt.child_batch_id
          WHERE bl.tenant_id = :tenantId AND bl.is_active = true AND lt.depth < 10
      )
      SELECT id, tenant_id, uid, created_at, created_by, updated_at, updated_by, is_active, deleted_at, version,
             parent_batch_id, child_batch_id, consumed_quantity, unit, consumption_percentage,
             consumed_at, process_reference, remarks
      FROM lineage_tree
      """,
      nativeQuery = true)
  List<BatchLineage> findDescendantsWithDepthLimit(
      @Param("batchId") UUID batchId, @Param("tenantId") UUID tenantId);
}
