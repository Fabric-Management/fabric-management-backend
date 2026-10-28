package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionChequeRepository extends JpaRepository<CollectionCheque, UUID> {
  Optional<CollectionCheque> findByTenantIdAndId(UUID tenant, UUID id);

  boolean existsByTenantIdAndClearedPaymentId(UUID tenant, UUID payment);

  boolean existsByTenantIdAndPartnerIdAndCurrencyAndInstrumentReferenceAndIdNot(
      UUID tenant, UUID partner, String currency, String reference, UUID id);
}
