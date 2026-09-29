package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Shared fibre categories. Every query names the catalogue owner explicitly (FIBER-CATALOG-1); RLS
 * is a second barrier, not the only filter.
 */
@Repository
public interface FiberCategoryRepository extends JpaRepository<FiberCategory, UUID> {

  List<FiberCategory> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID catalogOwnerId);

  Optional<FiberCategory> findByTenantIdAndId(UUID catalogOwnerId, UUID id);

  Optional<FiberCategory> findByTenantIdAndCategoryCode(UUID catalogOwnerId, String categoryCode);
}
