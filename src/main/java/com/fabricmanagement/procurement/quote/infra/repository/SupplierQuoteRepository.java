package com.fabricmanagement.procurement.quote.infra.repository;

import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierQuoteRepository extends JpaRepository<SupplierQuote, UUID> {
  Optional<SupplierQuote> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);
}
