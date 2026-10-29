package com.fabricmanagement.product.fabric.infra.repository;

import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricFinishingPriceRepository extends JpaRepository<FabricFinishingPrice, UUID> {

  List<FabricFinishingPrice> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  List<FabricFinishingPrice> findByTenantIdAndProcessIdAndDeletedAtIsNull(
      UUID tenantId, UUID processId);

  Optional<FabricFinishingPrice> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);
}
