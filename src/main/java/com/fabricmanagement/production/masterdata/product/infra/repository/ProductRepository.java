package com.fabricmanagement.production.masterdata.product.infra.repository;

import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Product entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

  Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Product> findByTenantIdAndIsActiveTrue(UUID tenantId);

  List<Product> findByTenantIdAndProductType(UUID tenantId, ProductType productType);

  List<Product> findByTenantIdAndProductTypeAndIsActiveTrue(UUID tenantId, ProductType productType);

  boolean existsByTenantIdAndId(UUID tenantId, UUID id);

  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  long countByTenantIdAndProductType(UUID tenantId, ProductType productType);
}
