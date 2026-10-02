package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Shared certification-scheme dictionary (a scheme name is not a certificate). Every query names
 * the catalogue owner explicitly (FIBER-CATALOG-1).
 */
@Repository
public interface FiberCertificationRepository extends JpaRepository<FiberCertification, UUID> {

  List<FiberCertification> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID catalogOwnerId);

  List<FiberCertification> findAllByTenantIdAndIdInAndIsActiveTrue(
      UUID catalogOwnerId, Collection<UUID> ids);

  Optional<FiberCertification> findByTenantIdAndIdAndIsActiveTrue(UUID catalogOwnerId, UUID id);

  Optional<FiberCertification> findByTenantIdAndCertificationCodeAndIsActiveTrue(
      UUID catalogOwnerId, String certificationCode);
}
