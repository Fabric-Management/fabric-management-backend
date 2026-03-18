package com.fabricmanagement.procurement.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // Fix #11
public class SupplierQuoteService {

  private final SupplierQuoteRepository quoteRepository;

  @Transactional
  public SupplierQuote createQuote(CreateSupplierQuoteRequest req) {
    SupplierQuote quote = new SupplierQuote();
    quote.setTenantId(TenantContext.getCurrentTenantId());

    // Fix #7 — Dinamik yıl
    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    quote.setQuoteNumber(String.format("SQ-%s-%s", year, suffix));

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

    SupplierQuote saved = quoteRepository.save(quote);
    log.info(
        "SupplierQuote created: {} [rfq={}, partner={}]",
        saved.getQuoteNumber(),
        req.getRfqId(),
        req.getTradingPartnerId());
    return saved;
  }

  @Transactional
  public SupplierQuote addLine(UUID quoteId, AddQuoteLineRequest req) {
    SupplierQuote quote = getActiveReceivedQuote(quoteId);

    // Fix #2 — DTO'dan entity oluşturuyoruz
    SupplierQuoteLine line = new SupplierQuoteLine();
    line.setTenantId(quote.getTenantId());
    line.setRfqLineId(req.getRfqLineId());
    line.setUnitPrice(req.getUnitPrice());
    line.setCurrency(req.getCurrency());
    line.setQty(req.getQty());
    line.setUnit(req.getUnit());
    if (req.getVolumeDiscounts() != null) {
      line.setVolumeDiscounts(req.getVolumeDiscounts());
    }
    line.setNotes(req.getNotes());

    quote.addLine(line);
    return quoteRepository.save(quote);
  }

  /**
   * Fix #4 — Sadece RECEIVED statüdeki teklif kabul edilebilir. Fix #5 — Kabul edilen teklifin
   * RFQ'sındaki diğer teklifler otomatik REJECTED yapılır.
   */
  @Transactional
  public SupplierQuote markAsAccepted(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);

    // Fix #4 — Kaynak statü kontrolü
    if (quote.getStatus() != SupplierQuoteStatus.RECEIVED) {
      throw new ProcurementDomainException("Cannot accept quote in status: " + quote.getStatus());
    }

    // Fix #5 — Aynı RFQ'daki diğer teklifleri REJECTED yap
    List<SupplierQuote> siblingsToReject =
        quoteRepository.findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
            quote.getRfqId(), quote.getTenantId(), SupplierQuoteStatus.RECEIVED);

    siblingsToReject.stream()
        .filter(q -> !q.getId().equals(quoteId))
        .forEach(
            q -> {
              q.setStatus(SupplierQuoteStatus.REJECTED);
              quoteRepository.save(q);
              log.info("Auto-rejected sibling quote {} (rfq={})", q.getQuoteNumber(), q.getRfqId());
            });

    quote.setStatus(SupplierQuoteStatus.ACCEPTED);
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote accepted: {} [rfq={}]", saved.getQuoteNumber(), saved.getRfqId());

    // TODO(phase-6+): SupplierQuoteAcceptedEvent → PurchaseOrder veya SubcontractOrder oluştur
    return saved;
  }

  /** Fix #4 — Source status validation: sadece RECEIVED statüdeki teklif reddedilebilir. */
  @Transactional
  public SupplierQuote markAsRejected(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);

    if (quote.getStatus() != SupplierQuoteStatus.RECEIVED) {
      throw new ProcurementDomainException("Cannot reject quote in status: " + quote.getStatus());
    }

    quote.setStatus(SupplierQuoteStatus.REJECTED);
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote rejected: {}", saved.getQuoteNumber());
    return saved;
  }

  // ── Private Helpers ────────────────────────────────────────────────────────

  /** Sadece RECEIVED statüdeki quote satır eklemeye açıktır. */
  private SupplierQuote getActiveReceivedQuote(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    if (quote.getStatus() != SupplierQuoteStatus.RECEIVED) {
      throw new ProcurementDomainException(
          "Cannot add line to quote in status: " + quote.getStatus());
    }
    return quote;
  }

  private SupplierQuote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> new ProcurementDomainException("SupplierQuote not found"));
  }
}
