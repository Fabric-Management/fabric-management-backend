package com.fabricmanagement.product.fabric.infra.repository;

import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricFinishingProcessRepository
    extends JpaRepository<FabricFinishingProcess, UUID> {

  List<FabricFinishingProcess> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  Optional<FabricFinishingProcess> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);

  Optional<FabricFinishingProcess> findByTenantIdAndCodeAndDeletedAtIsNull(
      UUID tenantId, String code);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
