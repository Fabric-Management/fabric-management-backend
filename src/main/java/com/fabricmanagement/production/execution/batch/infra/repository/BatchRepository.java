package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
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

  List<Batch> findByTenantIdAndStatus(UUID tenantId, BatchStatus status);

  List<Batch> findByTenantIdAndMaterialIdAndStatusIn(
      UUID tenantId, UUID materialId, Collection<BatchStatus> statuses);

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
