package com.fabricmanagement.production.execution.inventory.infra.repository;

import com.fabricmanagement.production.execution.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.execution.inventory.domain.InventoryTransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

  List<InventoryTransaction> findByTenantIdAndBatchIdAndIsActiveTrueOrderByTransactionDateDesc(
      UUID tenantId, UUID batchId);

  List<InventoryTransaction> findByTenantIdAndBatchIdAndTransactionTypeAndIsActiveTrue(
      UUID tenantId, UUID batchId, InventoryTransactionType type);

  List<InventoryTransaction> findByTenantIdAndIsActiveTrueOrderByTransactionDateDesc(UUID tenantId);

  @Query(
      "SELECT COALESCE(SUM(t.quantity), 0) "
          + "FROM InventoryTransaction t "
          + "WHERE t.batchId = :batchId "
          + "AND t.transactionType = :type "
          + "AND t.isActive = true")
  java.math.BigDecimal sumQuantityByBatchIdAndType(
      @Param("batchId") UUID batchId, @Param("type") InventoryTransactionType type);
}
