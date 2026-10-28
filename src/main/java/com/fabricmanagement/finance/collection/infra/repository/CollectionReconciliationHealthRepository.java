package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionReconciliationHealth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionReconciliationHealthRepository
    extends JpaRepository<CollectionReconciliationHealth, UUID> {
  Optional<CollectionReconciliationHealth> findByTenantId(UUID tenant);
}
