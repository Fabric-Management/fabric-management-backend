package com.fabricmanagement.production.masterdata.product.infra.repository;

import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, UUID> {

  List<ProductAttribute> findByIsActiveTrue();

  List<ProductAttribute> findByIsActiveTrueAndProductScopeIn(List<String> scopes);

  /**
   * Tenant-scoped code lookup. Attribute codes repeat across tenants (each tenant carries its own
   * copy of the reference rows), and integration tests run as a superuser DB role that bypasses RLS
   * — so an unscoped code lookup is not single-result-safe. {@code findFirst} keeps the query safe
   * even if a tenant ever ends up with duplicate rows for the same code.
   */
  Optional<ProductAttribute> findFirstByTenantIdAndAttributeCode(
      UUID tenantId, String attributeCode);
}
