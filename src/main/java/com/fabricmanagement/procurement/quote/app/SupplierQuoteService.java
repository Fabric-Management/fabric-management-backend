package com.fabricmanagement.procurement.quote.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteReceivedEvent;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fabricmanagement.procurement.quote.mapper.SupplierQuoteMapper;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SupplierQuoteService {

  private final SupplierQuoteRepository quoteRepository;
  private final SupplierRFQRepository rfqRepository;
  private final TradingPartnerResolver partnerResolver;
  private final SupplierQuoteMapper quoteMapper;
  private final DomainEventPublisher eventPublisher;

  public SupplierQuoteResponse getQuoteById(UUID quoteId) {
    return quoteMapper.toResponse(getActiveQuote(quoteId));
  }

  public PagedResponse<SupplierQuoteResponse> listQuotes(
      SupplierQuoteStatus status, UUID rfqId, UUID tradingPartnerId, Pageable pageable) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Specification<SupplierQuote> spec =
        Specification.<SupplierQuote>where(
                (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId))
            .and((root, query, cb) -> cb.isTrue(root.get("isActive")));

    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (rfqId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("rfqId"), rfqId));
    }
    if (tradingPartnerId != null) {
      spec =
          spec.and((root, query, cb) -> cb.equal(root.get("tradingPartnerId"), tradingPartnerId));
    }

    Page<SupplierQuote> page = quoteRepository.findAll(spec, pageable);
    return PagedResponse.from(page, quoteMapper::toResponse);
  }

  public List<SupplierQuoteResponse> getQuotesByRfq(UUID rfqId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return quoteRepository
        .findByTenantIdAndRfqIdAndIsActiveTrueOrderByCreatedAtDesc(tenantId, rfqId)
        .stream()
        .map(quoteMapper::toResponse)
        .toList();
  }

  @Transactional
  public SupplierQuoteResponse createQuote(CreateSupplierQuoteRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    SupplierQuote quote = new SupplierQuote();
    quote.setTenantId(tenantId);

    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    quote.setQuoteNumber(String.format("SQ-%s-%s", year, suffix));

    SupplierRFQ parentRfq =
        rfqRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, req.rfqId())
            .orElseThrow(() -> new ProcurementDomainException("RFQ not found"));

    if (parentRfq.getModuleType() != null
        && req.moduleType() != null
        && !parentRfq.getModuleType().name().equals(req.moduleType().name())) {
      throw new ProcurementDomainException("Quote moduleType must match parent RFQ moduleType");
    }

    quote.setRfqId(req.rfqId());
    quote.setTradingPartnerId(req.tradingPartnerId());
    quote.setValidUntil(req.validUntil());
    quote.setCurrency(req.currency());
    quote.setPaymentTerms(req.paymentTerms());
    quote.setLeadTimeDays(req.leadTimeDays());
    quote.setEntryMethod(req.entryMethod());
    quote.setModuleType(req.moduleType());
    quote.setNotes(req.notes());
    quote.setStatus(SupplierQuoteStatus.RECEIVED);

    SupplierQuote saved = quoteRepository.save(quote);
    log.info(
        "SupplierQuote created: {} [rfq={}, partner={}]",
        saved.getQuoteNumber(),
        req.rfqId(),
        req.tradingPartnerId());

    publishReceivedEvent(saved);

    return quoteMapper.toResponse(saved);
  }

  @Transactional
  public SupplierQuoteResponse addLine(UUID quoteId, AddQuoteLineRequest req) {
    SupplierQuote quote = getActiveQuote(quoteId);

    SupplierQuoteLine line = new SupplierQuoteLine();
    line.setTenantId(quote.getTenantId());
    line.setRfqLineId(req.rfqLineId());
    line.setUnitPrice(req.unitPrice());
    line.setCurrency(req.currency());
    line.setQty(req.qty());
    line.setUnit(req.unit());
    if (req.volumeDiscounts() != null && !req.volumeDiscounts().isEmpty()) {
      line.setVolumeDiscounts(req.volumeDiscounts());
    }
    if (req.moduleSpecs() != null) {
      line.setModuleSpecs(req.moduleSpecs());
    }
    line.setNotes(req.notes());

    quote.addLine(line);
    return quoteMapper.toResponse(quoteRepository.save(quote));
  }

  @Transactional
  public SupplierQuoteResponse startReview(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    quote.startReview();
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote review started: {}", saved.getQuoteNumber());
    return quoteMapper.toResponse(saved);
  }

  @Transactional
  public SupplierQuoteResponse markAsAccepted(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);

    // Auto-reject siblings — tek query ile RECEIVED + UNDER_REVIEW çek
    List<SupplierQuote> receivedSiblings =
        quoteRepository.findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
            quote.getRfqId(), quote.getTenantId(), SupplierQuoteStatus.RECEIVED);
    List<SupplierQuote> underReviewSiblings =
        quoteRepository.findByRfqIdAndTenantIdAndStatusAndIsActiveTrue(
            quote.getRfqId(), quote.getTenantId(), SupplierQuoteStatus.UNDER_REVIEW);

    Stream.concat(receivedSiblings.stream(), underReviewSiblings.stream())
        .filter(q -> !q.getId().equals(quoteId))
        .forEach(
            q -> {
              try {
                q.reject();
                quoteRepository.save(q);
                log.info(
                    "Auto-rejected sibling quote {} (rfq={})", q.getQuoteNumber(), q.getRfqId());
              } catch (RuntimeException ex) {
                log.warn("Could not auto-reject quote {}: {}", q.getId(), ex.getMessage());
              }
            });

    quote.accept();
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote accepted: {} [rfq={}]", saved.getQuoteNumber(), saved.getRfqId());

    eventPublisher.publish(
        new SupplierQuoteAcceptedEvent(saved.getTenantId(), saved.getId(), saved.getRfqId()));

    return quoteMapper.toResponse(saved);
  }

  @Transactional
  public SupplierQuoteResponse markAsRejected(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    quote.reject();
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote rejected: {}", saved.getQuoteNumber());
    return quoteMapper.toResponse(saved);
  }

  // ── Private Helpers ────────────────────────────────────────────────────────

  private SupplierQuote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> new ProcurementDomainException("SupplierQuote not found"));
  }

  private void publishReceivedEvent(SupplierQuote quote) {
    try {
      String supplierName =
          partnerResolver
              .resolvePartner(quote.getTenantId(), quote.getTradingPartnerId())
              .map(TradingPartner::getDisplayName)
              .orElse("Unknown Supplier");

      UUID rfqCreatedBy =
          rfqRepository
              .findByTenantIdAndIdAndIsActiveTrue(quote.getTenantId(), quote.getRfqId())
              .map(SupplierRFQ::getCreatedBy)
              .orElse(null);

      eventPublisher.publish(
          new SupplierQuoteReceivedEvent(
              quote.getTenantId(),
              quote.getId(),
              quote.getRfqId(),
              quote.getTradingPartnerId(),
              supplierName,
              rfqCreatedBy));
    } catch (Exception e) {
      log.warn("Failed to publish SupplierQuoteReceivedEvent for quote {}", quote.getId(), e);
    }
  }
}
