package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.FiberTestStandard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiberTestStandardRepository extends JpaRepository<FiberTestStandard, UUID> {

  Optional<FiberTestStandard> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FiberTestStandard> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  @Query(
      """
      SELECT standard FROM FiberTestStandard standard
      WHERE standard.tenantId = :tenantId AND standard.isActive = true
        AND (locate(lower(:search), lower(standard.code)) > 0
          OR locate(lower(:search), lower(standard.name)) > 0)
      ORDER BY standard.code, standard.revision, standard.id
      """)
  Page<FiberTestStandard> searchActive(
      @Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);
}
