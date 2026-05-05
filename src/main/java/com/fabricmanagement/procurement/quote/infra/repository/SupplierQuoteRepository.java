package com.fabricmanagement.procurement.quote.infra.repository;

import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierQuoteRepository
    extends JpaRepository<SupplierQuote, UUID>, JpaSpecificationExecutor<SupplierQuote> {

  Optional<SupplierQuote> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  /** Fix #5 — Aynı RFQ'daki rakip teklifleri bulmak için (auto-reject). */
  List<SupplierQuote> findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
      UUID rfqId, UUID tenantId, SupplierQuoteStatus status);

  List<SupplierQuote> findByTenantIdAndRfqIdAndIsActiveTrueOrderByCreatedAtDesc(
      UUID tenantId, UUID rfqId);

  List<SupplierQuote> findByValidUntilBeforeAndStatusInAndIsActiveTrue(
      LocalDate validUntil, List<SupplierQuoteStatus> statuses);
}
