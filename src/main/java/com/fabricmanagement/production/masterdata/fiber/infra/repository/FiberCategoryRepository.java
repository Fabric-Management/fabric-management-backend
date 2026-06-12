package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberCategoryRepository extends JpaRepository<FiberCategory, UUID> {
  List<FiberCategory> findByIsActiveTrue();

  /** Tenant-scoped active categories — prevents double rows when RLS carve-out is active. */
  List<FiberCategory> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /** Find by category code (e.g. NATURAL_PLANT). Used for fiber_type mapping. */
  Optional<FiberCategory> findByCategoryCode(String categoryCode);
}
