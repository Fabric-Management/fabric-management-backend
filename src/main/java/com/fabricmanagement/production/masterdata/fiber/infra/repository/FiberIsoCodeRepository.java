package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberIsoCodeRepository extends JpaRepository<FiberIsoCode, UUID> {

  List<FiberIsoCode> findByIsActiveTrue();

  /** Tenant-scoped active ISO codes — prevents double rows when RLS carve-out is active. */
  List<FiberIsoCode> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /** Task F1: Only official ISO 2076 codes (52 records). Used when baseOnly=true. */
  List<FiberIsoCode> findByIsOfficialIsoTrueAndIsActiveTrue();

  Optional<FiberIsoCode> findByIsoCode(String isoCode);

  /** Case-insensitive check: ISO code already exists in catalog. */
  boolean existsByIsoCodeIgnoreCase(String isoCode);
}
