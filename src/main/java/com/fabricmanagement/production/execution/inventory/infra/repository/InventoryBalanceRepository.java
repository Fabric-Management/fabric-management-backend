package com.fabricmanagement.production.execution.inventory.infra.repository;

import com.fabricmanagement.production.execution.inventory.domain.InventoryBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {
  Optional<InventoryBalance> findByBatchIdAndLocationId(UUID batchId, UUID locationId);

  Optional<InventoryBalance> findByBatchIdAndLocationIdIsNull(UUID batchId);

  Page<InventoryBalance> findByBatchId(UUID batchId, Pageable pageable);

  Page<InventoryBalance> findByLocationId(UUID locationId, Pageable pageable);
}
