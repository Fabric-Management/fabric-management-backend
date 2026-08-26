package com.fabricmanagement.production.core.inventory.infra.repository;

import com.fabricmanagement.production.core.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.core.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.core.inventory.domain.enums.ReferenceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
  Page<InventoryTransaction> findByBatchId(UUID batchId, Pageable pageable);

  Page<InventoryTransaction> findByBatchIdAndTransactionType(
      UUID batchId, InventoryTransactionType transactionType, Pageable pageable);

  List<InventoryTransaction> findByReferenceIdAndReferenceType(
      UUID referenceId, ReferenceType referenceType);
}
