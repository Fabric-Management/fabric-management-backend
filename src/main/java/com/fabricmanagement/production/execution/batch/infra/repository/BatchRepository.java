package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the universal {@link Batch} entity.
 *
 * <p>Provides tenant-scoped and material-type-scoped queries for batch lookup and listing. Use
 * derived query methods for filtering by materialId, materialType, batchCode, or status.
 */
@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

  List<Batch> findByTenantId(UUID tenantId);

  List<Batch> findByTenantIdAndIsActiveTrue(UUID tenantId);

  @Lock(LockModeType.OPTIMISTIC)
  Optional<Batch> findByIdAndTenantId(UUID id, UUID tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Batch b WHERE b.id = :id AND b.tenantId = :tenantId")
  Optional<Batch> findByIdAndTenantIdForUpdate(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId);

  List<Batch> findByTenantIdAndMaterialId(UUID tenantId, UUID materialId);

  List<Batch> findByTenantIdAndMaterialIdAndIsActiveTrue(UUID tenantId, UUID materialId);

  Optional<Batch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);

  boolean existsByTenantIdAndBatchCode(UUID tenantId, String batchCode);

  /**
   * Find the first output batch produced by a specific source (e.g., WorkOrder). Used for cost
   * calculation to determine output material and module type.
   */
  Optional<Batch> findFirstByTenantIdAndSourceIdAndSourceType(
      UUID tenantId, UUID sourceId, BatchSourceType sourceType);

  /** Count batches that are direct children of the given parent (for split code generation). */
  long countByTenantIdAndParentBatchId(UUID tenantId, UUID parentBatchId);

  List<Batch> findByTenantIdAndStatus(UUID tenantId, BatchStatus status);

  List<Batch> findByTenantIdAndStatusIn(UUID tenantId, Collection<BatchStatus> statuses);

  List<Batch> findByTenantIdAndMaterialIdAndStatusIn(
      UUID tenantId, UUID materialId, Collection<BatchStatus> statuses);

  /**
   * Find all batches for a given material (any tenant). Prefer tenant-scoped {@link
   * #findByTenantIdAndMaterialId(UUID, UUID)} in multi-tenant contexts.
   */
  List<Batch> findAllByMaterialId(UUID materialId);

  /**
   * Find all batches of a given material type (e.g. FIBER, YARN). Prefer tenant-scoped queries when
   * tenant context is available.
   */
  List<Batch> findAllByMaterialType(MaterialType materialType);

  /**
   * Find a batch by its unique batch code. In multi-tenant systems prefer {@link
   * #findByTenantIdAndBatchCode(UUID, String)} to scope by tenant.
   */
  Optional<Batch> findByBatchCode(String batchCode);

  /**
   * Find all batches in a given status. Prefer {@link #findByTenantIdAndStatus(UUID, BatchStatus)}
   * when tenant context is available.
   */
  List<Batch> findAllByStatus(BatchStatus status);

  /**
   * Returns true if the given fiber has at least one batch in any of the given statuses.
   *
   * <p>Used by {@code FiberService.deactivateFiber()} to block deactivation when {@code statuses =
   * BatchStatus.PRODUCTION_ACTIVE} — i.e. the fiber still has batches in RESERVED or IN_PROGRESS
   * state on the production floor.
   *
   * <p>This query is index-friendly: {@code (tenant_id, material_id, status)} and returns as soon
   * as one matching row is found.
   */
  boolean existsByTenantIdAndMaterialIdAndStatusIn(
      UUID tenantId, UUID materialId, Collection<BatchStatus> statuses);
}
