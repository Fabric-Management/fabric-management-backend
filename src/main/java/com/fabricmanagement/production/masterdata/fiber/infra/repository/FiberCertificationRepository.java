package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberCertificationRepository extends JpaRepository<FiberCertification, UUID> {

  List<FiberCertification> findByIsActiveTrue();

  List<FiberCertification> findAllByIdInAndIsActiveTrue(java.util.Collection<UUID> ids);

  /** Find certification by id only if it is active (used when adding to batch). */
  Optional<FiberCertification> findByIdAndIsActiveTrue(UUID id);

  Optional<FiberCertification> findByCertificationCode(String certificationCode);
}
