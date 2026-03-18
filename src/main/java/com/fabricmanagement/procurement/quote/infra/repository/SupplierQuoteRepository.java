package com.fabricmanagement.procurement.quote.infra.repository;

import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierQuoteRepository extends JpaRepository<SupplierQuote, UUID> {

  Optional<SupplierQuote> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  /** Fix #5 — Aynı RFQ'daki rakip teklifleri bulmak için (auto-reject). */
  List<SupplierQuote> findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
      UUID rfqId, UUID tenantId, SupplierQuoteStatus status);
}
