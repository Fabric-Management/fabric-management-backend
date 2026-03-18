package com.fabricmanagement.production.execution.lineage.infra.persistence;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttributeInheritanceSchemaRepository
    extends JpaRepository<AttributeInheritanceSchemaEntity, UUID> {
  Optional<AttributeInheritanceSchemaEntity>
      findByTenantIdAndSourceTypeAndTargetTypeAndIsActiveTrue(
          UUID tenantId, MaterialType sourceType, MaterialType targetType);
}
