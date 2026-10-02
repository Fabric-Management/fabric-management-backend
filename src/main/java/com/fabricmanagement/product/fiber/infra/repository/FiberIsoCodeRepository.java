package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Shared ISO fibre codes: one row per code, owned by the catalogue owner. Every query names that
 * owner explicitly (FIBER-CATALOG-1). Approval never inserts here; new codes are platform catalogue
 * releases.
 */
@Repository
public interface FiberIsoCodeRepository extends JpaRepository<FiberIsoCode, UUID> {

  List<FiberIsoCode> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID catalogOwnerId);

  /** {@code baseOnly=true}: codes classified as official, within the shared owner. */
  List<FiberIsoCode> findByTenantIdAndIsOfficialIsoTrueAndIsActiveTrueOrderByDisplayOrderAsc(
      UUID catalogOwnerId);

  Optional<FiberIsoCode> findByTenantIdAndId(UUID catalogOwnerId, UUID id);

  /** Codes are normalised (trim + upper) at every boundary; lookup stays case-insensitive. */
  Optional<FiberIsoCode> findByTenantIdAndIsoCodeIgnoreCase(UUID catalogOwnerId, String isoCode);
}
