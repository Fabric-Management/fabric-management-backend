package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequest;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberRequestRepository extends JpaRepository<FiberRequest, UUID> {

  Page<FiberRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  Page<FiberRequest> findByStatusOrderByCreatedAtDesc(FiberRequestStatus status, Pageable pageable);

  /** Case-insensitive duplicate check for tenant + ISO code. */
  boolean existsByTenantIdAndIsoCodeIgnoreCaseAndStatusIn(
      UUID tenantId, String isoCode, List<FiberRequestStatus> statuses);

  Optional<FiberRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}
