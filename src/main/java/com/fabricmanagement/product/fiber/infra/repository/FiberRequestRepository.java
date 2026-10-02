package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.FiberRequest;
import com.fabricmanagement.product.fiber.domain.FiberRequestStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberRequestRepository extends JpaRepository<FiberRequest, UUID> {

  Page<FiberRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  Page<FiberRequest> findByStatusOrderByCreatedAtDesc(FiberRequestStatus status, Pageable pageable);

  /** NULL-safe duplicate check over the logical request key. */
  @Query(
      "SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM FiberRequest r "
          + "WHERE r.tenantId = :tenantId AND upper(r.isoCode) = upper(:isoCode) "
          + "AND ((r.materialSource = :materialSource) "
          + "OR (r.materialSource IS NULL AND :materialSource IS NULL)) "
          + "AND r.status IN :statuses")
  boolean existsActiveLogicalDuplicate(
      @Param("tenantId") UUID tenantId,
      @Param("isoCode") String isoCode,
      @Param("materialSource") MaterialSource materialSource,
      @Param("statuses") List<FiberRequestStatus> statuses);

  Optional<FiberRequest> findByTenantIdAndId(UUID tenantId, UUID id);

  /** Serialises concurrent decisions on one request: the second waits, then sees the outcome. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM FiberRequest r WHERE r.id = :id")
  Optional<FiberRequest> findByIdForUpdate(@Param("id") UUID id);
}
