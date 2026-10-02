package com.fabricmanagement.production.core.stockunit.infra.repository;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockUnitCutRepository extends JpaRepository<StockUnitCut, UUID> {

  List<StockUnitCut> findByTenantIdAndStockUnitIdInAndIsActiveTrue(
      UUID tenantId, Collection<UUID> stockUnitIds);

  Optional<StockUnitCut> findByTenantIdAndIdAndStockUnitId(
      UUID tenantId, UUID id, UUID stockUnitId);
}
