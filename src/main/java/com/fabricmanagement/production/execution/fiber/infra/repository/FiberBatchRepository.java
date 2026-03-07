package com.fabricmanagement.production.execution.fiber.infra.repository;

import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatchStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberBatchRepository extends JpaRepository<FiberBatch, UUID> {

  List<FiberBatch> findByTenantId(UUID tenantId);

  List<FiberBatch> findByTenantIdAndIsActiveTrue(UUID tenantId);

  @Lock(LockModeType.OPTIMISTIC)
  Optional<FiberBatch> findByIdAndTenantId(UUID id, UUID tenantId);

  List<FiberBatch> findByTenantIdAndFiberId(UUID tenantId, UUID fiberId);

  List<FiberBatch> findByTenantIdAndFiberIdAndIsActiveTrue(UUID tenantId, UUID fiberId);

  Optional<FiberBatch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);

  boolean existsByTenantIdAndBatchCode(UUID tenantId, String batchCode);

  List<FiberBatch> findByTenantIdAndStatus(UUID tenantId, FiberBatchStatus status);

  List<FiberBatch> findByTenantIdAndFiberIdAndStatusIn(
      UUID tenantId, UUID fiberId, Collection<FiberBatchStatus> statuses);

  /**
   * Returns true if the given fiber has at least one batch in any of the given statuses.
   *
   * <p>Used by {@code FiberService.deactivateFiber()} to block deactivation when {@code statuses =
   * FiberBatchStatus.PRODUCTION_ACTIVE} — i.e. the fiber still has batches in RESERVED or
   * IN_PROGRESS state on the production floor.
   *
   * <p>This query is index-friendly: {@code (tenant_id, fiber_id, status)} and returns as soon as
   * one matching row is found.
   */
  boolean existsByTenantIdAndFiberIdAndStatusIn(
      UUID tenantId, UUID fiberId, Collection<FiberBatchStatus> statuses);
}
