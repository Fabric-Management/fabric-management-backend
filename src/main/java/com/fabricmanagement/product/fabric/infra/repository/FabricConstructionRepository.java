package com.fabricmanagement.product.fabric.infra.repository;

import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricConstructionRepository extends JpaRepository<FabricConstruction, UUID> {

  List<FabricConstruction> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  Optional<FabricConstruction> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);

  Optional<FabricConstruction> findByTenantIdAndCodeAndDeletedAtIsNull(UUID tenantId, String code);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
