package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Tenant quality profiles. Every query is tenant-scoped (FIBER-CATALOG-1). */
@Repository
public interface FiberQualityStandardRepository extends JpaRepository<FiberQualityStandard, UUID> {

  Optional<FiberQualityStandard> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FiberQualityStandard> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  List<FiberQualityStandard> findByTenantIdAndIsActiveTrue(UUID tenantId);

  List<FiberQualityStandard> findByTenantIdAndTargetTypeAndIsoCode_IdAndIsActiveTrue(
      UUID tenantId, FiberQualityTargetType targetType, UUID isoCodeId);

  List<FiberQualityStandard> findByTenantIdAndTargetTypeAndFiberIdAndIsActiveTrue(
      UUID tenantId, FiberQualityTargetType targetType, UUID fiberId);

  Optional<FiberQualityStandard>
      findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
          UUID tenantId, FiberQualityTargetType targetType, UUID isoCodeId);

  Optional<FiberQualityStandard>
      findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
          UUID tenantId, FiberQualityTargetType targetType, UUID fiberId);

  boolean existsByTenantIdAndTargetTypeAndIsoCode_IdAndStandardNameAndIsActiveTrue(
      UUID tenantId, FiberQualityTargetType targetType, UUID isoCodeId, String standardName);

  boolean existsByTenantIdAndTargetTypeAndFiberIdAndStandardNameAndIsActiveTrue(
      UUID tenantId, FiberQualityTargetType targetType, UUID fiberId, String standardName);

  /** Serialises default selection for one tenant and target inside the current transaction. */
  @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:lockKey))", nativeQuery = true)
  void acquireTargetLock(@Param("lockKey") String lockKey);
}
