package com.fabricmanagement.production.execution.lineage.infra.persistence;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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
