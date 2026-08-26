package com.fabricmanagement.product.core.infra.repository;

import com.fabricmanagement.product.core.domain.registry.PropertyDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyDefinitionRepository extends JpaRepository<PropertyDefinition, UUID> {

  Optional<PropertyDefinition> findByTenantIdAndPropertyKey(UUID tenantId, String propertyKey);

  List<PropertyDefinition> findByTenantId(UUID tenantId);

  List<PropertyDefinition> findByTenantIdAndSystemDefinedTrue(UUID tenantId);

  boolean existsByTenantIdAndPropertyKey(UUID tenantId, String propertyKey);
}
