package com.fabricmanagement.procurement.quote.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
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
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private final ExchangeRateService exchangeRateService;
  private final TenantReportingCurrencyPort tenantReportingCurrencyPort;

  public SupplierQuoteResponse getQuoteById(UUID quoteId) {
    return toResponse(getActiveQuote(quoteId));
  }

  public PagedResponse<SupplierQuoteResponse> listQuotes(
      SupplierQuoteStatus status, UUID rfqId, UUID tradingPartnerId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();

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
    Map<UUID, SupplierRFQLine> rfqLinesById = loadRfqLines(page.getContent());
    return PagedResponse.from(page, quote -> quoteMapper.toResponse(quote, rfqLinesById));
  }

  public List<SupplierQuoteResponse> getQuotesByRfq(UUID rfqId) {
    UUID tenantId = TenantContext.requireTenantId();
    List<SupplierQuote> quotes =
        quoteRepository.findByTenantIdAndRfqIdAndIsActiveTrueOrderByCreatedAtDesc(tenantId, rfqId);
    Map<UUID, SupplierRFQLine> rfqLinesById = loadRfqLines(quotes);
    return quotes.stream().map(quote -> quoteMapper.toResponse(quote, rfqLinesById)).toList();
  }

  @Transactional
  public SupplierQuoteResponse createQuote(CreateSupplierQuoteRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
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

    return quoteMapper.toResponse(saved, indexRfqLines(parentRfq));
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
    recomputeTotals(quote);
    return toResponse(quoteRepository.save(quote));
  }

  @Transactional
  public SupplierQuoteResponse startReview(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    quote.startReview();
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote review started: {}", saved.getQuoteNumber());
    return toResponse(saved);
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

    return toResponse(saved);
  }

  @Transactional
  public SupplierQuoteResponse markAsRejected(UUID quoteId) {
    SupplierQuote quote = getActiveQuote(quoteId);
    quote.reject();
    SupplierQuote saved = quoteRepository.save(quote);
    log.info("SupplierQuote rejected: {}", saved.getQuoteNumber());
    return toResponse(saved);
  }

  // ── Private Helpers ────────────────────────────────────────────────────────

  private SupplierQuoteResponse toResponse(SupplierQuote quote) {
    return quoteMapper.toResponse(quote, loadRfqLines(List.of(quote)));
  }

  private Map<UUID, SupplierRFQLine> loadRfqLines(List<SupplierQuote> quotes) {
    if (quotes.isEmpty()) {
      return Map.of();
    }

    UUID tenantId = TenantContext.requireTenantId();
    List<UUID> rfqIds = quotes.stream().map(SupplierQuote::getRfqId).distinct().toList();
    return rfqRepository.findAllByTenantIdAndIdInAndIsActiveTrue(tenantId, rfqIds).stream()
        .flatMap(rfq -> rfq.getLines().stream())
        .collect(
            Collectors.toMap(
                SupplierRFQLine::getId, Function.identity(), (first, duplicate) -> first));
  }

  private Map<UUID, SupplierRFQLine> indexRfqLines(SupplierRFQ rfq) {
    return rfq.getLines().stream()
        .collect(
            Collectors.toMap(
                SupplierRFQLine::getId, Function.identity(), (first, duplicate) -> first));
  }

  private SupplierQuote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
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

  private void recomputeTotals(SupplierQuote quote) {
    LocalDate documentDate = docDate(quote);
    String headerCurrency = quote.getCurrency();
    BigDecimal nativeTotal =
        quote.getLines().stream()
            .map(
                line ->
                    convertLineTotalToHeader(
                        quote.getTenantId(), line, headerCurrency, documentDate))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    String reportingCurrency =
        tenantReportingCurrencyPort.getReportingCurrency(quote.getTenantId());
    quote.setReportingTotal(
        convertMoney(
            quote.getTenantId(), nativeTotal, headerCurrency, reportingCurrency, documentDate));
  }

  private BigDecimal convertLineTotalToHeader(
      UUID tenantId, SupplierQuoteLine line, String headerCurrency, LocalDate documentDate) {
    BigDecimal lineAmount = line.lineTotal();
    String lineCurrency = line.getCurrency();
    if (lineCurrency.equalsIgnoreCase(headerCurrency)) {
      return lineAmount;
    }

    return convertMoney(tenantId, lineAmount, lineCurrency, headerCurrency, documentDate)
        .getConvertedAmount();
  }

  private ConvertedMoney convertMoney(
      UUID tenantId,
      BigDecimal amount,
      String fromCurrency,
      String toCurrency,
      LocalDate documentDate) {
    if (fromCurrency.equalsIgnoreCase(toCurrency)) {
      return ConvertedMoney.of(
          amount, fromCurrency, amount, toCurrency, BigDecimal.ONE, documentDate);
    }

    try {
      return exchangeRateService.convert(tenantId, amount, fromCurrency, toCurrency, documentDate);
    } catch (ExchangeRateRequiredException ex) {
      throw new ProcurementDomainException(
          String.format(
              "No exchange rate for %s->%s on %s; seed a rate before creating this document",
              fromCurrency, toCurrency, documentDate),
          ex);
    }
  }

  private LocalDate docDate(SupplierQuote quote) {
    Instant dateSource =
        quote.getSubmittedAt() != null ? quote.getSubmittedAt() : quote.getCreatedAt();
    return dateSource != null ? dateSource.atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now();
  }
}
