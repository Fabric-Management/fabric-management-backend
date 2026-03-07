package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberSpecification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberSpecificationRepository extends JpaRepository<FiberSpecification, UUID> {

  List<FiberSpecification> findByTenantIdAndFiberIdAndIsActiveTrue(UUID tenantId, UUID fiberId);

  Optional<FiberSpecification> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FiberSpecification> findByTenantIdAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
      UUID tenantId, UUID fiberId);

  boolean existsByTenantIdAndFiberIdAndSpecName(UUID tenantId, UUID fiberId, String specName);

  List<FiberSpecification> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
