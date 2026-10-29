package com.fabricmanagement.product.fabric.infra.repository;

import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricFinishingPriceClassRepository
    extends JpaRepository<FabricFinishingPriceClass, UUID> {

  List<FabricFinishingPriceClass> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  Optional<FabricFinishingPriceClass> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
