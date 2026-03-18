package com.fabricmanagement.sales.catalog.infra.repository;

import com.fabricmanagement.sales.catalog.domain.ProductCatalog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, UUID> {

  List<ProductCatalog> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

  List<ProductCatalog> findAllByTenantIdAndModuleTypeAndIsActiveTrue(
      UUID tenantId, String moduleType);

  Optional<ProductCatalog> findByTenantIdAndMaterialIdAndIsActiveTrue(
      UUID tenantId, UUID materialId);

  @Query(
      """
      SELECT pc FROM ProductCatalog pc
      WHERE pc.tenantId = :tenantId
        AND pc.materialId = :materialId
        AND pc.isActive = true
      """)
  Optional<ProductCatalog> findActiveByMaterialId(
      @Param("tenantId") UUID tenantId, @Param("materialId") UUID materialId);
}
