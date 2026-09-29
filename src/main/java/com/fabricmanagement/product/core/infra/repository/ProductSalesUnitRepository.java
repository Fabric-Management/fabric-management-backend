package com.fabricmanagement.product.core.infra.repository;

import com.fabricmanagement.product.core.domain.ProductSalesUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSalesUnitRepository extends JpaRepository<ProductSalesUnit, UUID> {

  List<ProductSalesUnit> findByTenantIdAndProductIdAndIsActiveTrueOrderByUnitAsc(
      UUID tenantId, UUID productId);

  List<ProductSalesUnit> findByTenantIdAndProductIdInAndIsActiveTrue(
      UUID tenantId, Collection<UUID> productIds);
}
