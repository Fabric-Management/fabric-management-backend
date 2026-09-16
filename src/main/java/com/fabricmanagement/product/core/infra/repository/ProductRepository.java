package com.fabricmanagement.product.core.infra.repository;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import java.util.Collection;
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
  @org.springframework.data.jpa.repository.Query(
      """
      select p.id as id, p.productType as productType, p.version as revision,
             p.createdAt as recordedAt, p.isActive as active
      from Product p where p.tenantId = :tenantId and p.id in :ids
      """)
  List<EvidenceReferenceRow> findEvidenceReferences(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
      @org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);

  interface EvidenceReferenceRow {
    UUID getId();

    ProductType getProductType();

    Long getRevision();

    java.time.Instant getRecordedAt();

    Boolean getActive();
  }

  Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Product> findByTenantIdInAndId(List<UUID> tenantIds, UUID id);

  List<Product> findByTenantIdAndIsActiveTrue(UUID tenantId);

  List<Product> findByTenantIdAndProductType(UUID tenantId, ProductType productType);

  List<Product> findByTenantIdInAndIsActiveTrue(List<UUID> tenantIds);

  List<Product> findByTenantIdAndProductTypeAndIsActiveTrue(UUID tenantId, ProductType productType);

  List<Product> findByTenantIdAndIdInAndProductType(
      UUID tenantId, Collection<UUID> ids, ProductType productType);

  List<Product> findByTenantIdInAndProductTypeAndIsActiveTrue(
      List<UUID> tenantIds, ProductType productType);

  boolean existsByTenantIdAndId(UUID tenantId, UUID id);

  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  long countByTenantIdAndProductType(UUID tenantId, ProductType productType);

  long countByTenantIdAndProductTypeAndIsActiveTrue(UUID tenantId, ProductType productType);
}
