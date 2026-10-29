package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionCommandReceiptRepository
    extends JpaRepository<CollectionCommandReceipt, UUID> {
  Optional<CollectionCommandReceipt> findByTenantIdAndOperationId(UUID tenant, UUID operation);
}
