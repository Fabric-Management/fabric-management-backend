package com.fabricmanagement.production.core.lineage.infra.persistence;

import com.fabricmanagement.product.core.domain.ProductType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttributeInheritanceSchemaRepository
    extends JpaRepository<AttributeInheritanceSchemaEntity, UUID> {
  Optional<AttributeInheritanceSchemaEntity>
      findByTenantIdAndSourceTypeAndTargetTypeAndIsActiveTrue(
          UUID tenantId, ProductType sourceType, ProductType targetType);
}
