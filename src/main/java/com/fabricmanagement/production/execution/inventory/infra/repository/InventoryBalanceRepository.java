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

  @org.springframework.data.jpa.repository.Query(
      value =
          """
          SELECT COALESCE(SUM(ib.quantity - ib.reserved_quantity - ib.consumed_quantity), 0)
          FROM production.production_execution_inventory_balance ib
          JOIN production.production_execution_batch b ON b.id = ib.batch_id
          WHERE b.tenant_id = :tenantId
            AND b.product_id = :productId
            AND b.is_active = true
            AND ib.is_active = true
          """,
      nativeQuery = true)
  java.math.BigDecimal sumAvailableByProduct(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
      @org.springframework.data.repository.query.Param("productId") UUID productId);
}
