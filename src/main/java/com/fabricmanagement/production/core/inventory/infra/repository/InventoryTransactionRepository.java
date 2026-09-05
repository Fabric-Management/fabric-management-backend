package com.fabricmanagement.production.core.inventory.infra.repository;

import com.fabricmanagement.production.core.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.core.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.core.inventory.domain.enums.ReferenceType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
  @Query(
      value =
          """
          SELECT DISTINCT batch.product_id
          FROM production.production_execution_inventory_transaction inventory_transaction
          JOIN production.production_execution_batch batch
            ON batch.id = inventory_transaction.batch_id
           AND batch.tenant_id = inventory_transaction.tenant_id
          WHERE inventory_transaction.tenant_id = :tenantId
            AND inventory_transaction.is_active = TRUE
            AND inventory_transaction.transaction_date >= :cutoff
            AND inventory_transaction.transaction_type IN (:transactionTypes)
          """,
      nativeQuery = true)
  List<UUID> findRecentlyMovedProductIds(
      @Param("tenantId") UUID tenantId,
      @Param("cutoff") Instant cutoff,
      @Param("transactionTypes") Collection<String> transactionTypes);

  Page<InventoryTransaction> findByBatchId(UUID batchId, Pageable pageable);

  Page<InventoryTransaction> findByBatchIdAndTransactionType(
      UUID batchId, InventoryTransactionType transactionType, Pageable pageable);

  List<InventoryTransaction> findByReferenceIdAndReferenceType(
      UUID referenceId, ReferenceType referenceType);
}
