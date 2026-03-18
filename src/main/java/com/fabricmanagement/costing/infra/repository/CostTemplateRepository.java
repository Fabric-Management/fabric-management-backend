package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.template.CostTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link CostTemplate}. */
public interface CostTemplateRepository extends JpaRepository<CostTemplate, UUID> {

  /** Find the default template for a given tenant and module type. */
  @Query(
      """
      SELECT ct FROM CostTemplate ct
      WHERE ct.tenantId = :tenantId
        AND ct.moduleType = :moduleType
        AND ct.defaultTemplate = true
        AND ct.isActive = true
      """)
  Optional<CostTemplate> findDefault(
      @Param("tenantId") UUID tenantId, @Param("moduleType") String moduleType);

  java.util.List<CostTemplate> findByTenantIdAndModuleTypeAndIsActiveTrue(
      UUID tenantId, String moduleType);
}
