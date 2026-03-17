package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.item.CostItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link CostItem}. */
public interface CostItemRepository extends JpaRepository<CostItem, UUID> {

  Optional<CostItem> findByCode(String code);

  /**
   * All active global items (tenant_id IS NULL) and active module-specific items for the given
   * module type. Global items are shared across all tenants — tenant_id = null is the convention.
   */
  @Query(
      """
      SELECT ci FROM CostItem ci
      WHERE ci.isActive = true
        AND (ci.scope = 'GLOBAL'
             OR (ci.scope = 'MODULE_SPECIFIC' AND ci.moduleType = :moduleType))
      ORDER BY ci.displayOrder
      """)
  List<CostItem> findActiveForModule(@Param("moduleType") String moduleType);

  List<CostItem> findByIsActiveTrueOrderByDisplayOrder();
}
