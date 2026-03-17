package com.fabricmanagement.procurement.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierQuoteService {

  private final SupplierQuoteRepository quoteRepository;

  @Transactional
  public SupplierQuote createQuote(CreateSupplierQuoteRequest req) {
    SupplierQuote quote = new SupplierQuote();
    quote.setTenantId(TenantContext.getCurrentTenantId());

    String uniqueSeq = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    quote.setQuoteNumber("SQ-2026-" + uniqueSeq);

    quote.setRfqId(req.getRfqId());
    quote.setTradingPartnerId(req.getTradingPartnerId());
    quote.setValidUntil(req.getValidUntil());
    quote.setCurrency(req.getCurrency());
    quote.setPaymentTerms(req.getPaymentTerms());
    quote.setLeadTimeDays(req.getLeadTimeDays());
    quote.setEntryMethod(req.getEntryMethod());
    quote.setNotes(req.getNotes());
    quote.setStatus(SupplierQuoteStatus.RECEIVED);
    quote.setSubmittedAt(Instant.now());

    return quoteRepository.save(quote);
  }

  @Transactional
  public SupplierQuote addLine(UUID quoteId, SupplierQuoteLine line) {
    SupplierQuote quote = getActiveQuote(quoteId);

    if (quote.getStatus() != SupplierQuoteStatus.RECEIVED) {
      throw new ProcurementDomainException(
          "Cannot add line to quote in status: " + quote.getStatus());
    }

    line.setTenantId(quote.getTenantId());
    quote.addLine(line);
    return quoteRepository.save(quote);
  }

  @Transactional
  public SupplierQuote markAsAccepted(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    quote.setStatus(SupplierQuoteStatus.ACCEPTED);
    return quoteRepository.save(quote);
  }

  @Transactional
  public SupplierQuote markAsRejected(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    quote.setStatus(SupplierQuoteStatus.REJECTED);
    return quoteRepository.save(quote);
  }

  private SupplierQuote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> new ProcurementDomainException("SupplierQuote not found"));
  }
}
