package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberQualityStandard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberQualityStandardRepository extends JpaRepository<FiberQualityStandard, UUID> {

  List<FiberQualityStandard> findByTenantIdAndIsoCode_IdAndIsActiveTrue(
      UUID tenantId, UUID isoCodeId);

  Optional<FiberQualityStandard> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FiberQualityStandard> findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
      UUID tenantId, UUID isoCodeId);

  boolean existsByTenantIdAndIsoCode_IdAndStandardName(
      UUID tenantId, UUID isoCodeId, String standardName);

  boolean existsByTenantIdAndIsoCode_IdAndStandardNameAndIdNot(
      UUID tenantId, UUID isoCodeId, String standardName, UUID excludeId);

  List<FiberQualityStandard> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
