package com.fabricmanagement.sales.salesproduct.infra.repository;

import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesProductRepository extends JpaRepository<SalesProduct, UUID> {

  List<SalesProduct> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

  List<SalesProduct> findAllByTenantIdAndModuleTypeAndIsActiveTrue(
      UUID tenantId, String moduleType);

  Optional<SalesProduct> findByTenantIdAndProductIdAndIsActiveTrue(UUID tenantId, UUID productId);

  /** Active catalogue entries of these products (general and customer-specific). */
  List<SalesProduct> findAllByTenantIdAndProductIdInAndIsActiveTrue(
      UUID tenantId, java.util.Collection<UUID> productIds);

  /** The general (not customer-specific) active catalogue entry used for list pricing. */
  @Query(
      """
      SELECT pc FROM SalesProduct pc
      WHERE pc.tenantId = :tenantId
        AND pc.productId = :productId
        AND pc.customerId IS NULL
        AND pc.isActive = true
      """)
  Optional<SalesProduct> findActiveByProductId(
      @Param("tenantId") UUID tenantId, @Param("productId") UUID productId);
}
