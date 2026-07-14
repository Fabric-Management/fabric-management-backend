package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.LikePattern;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.platform.tradingpartner.app.PartnerContactService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContact;
import com.fabricmanagement.platform.user.app.UserDisplayNameResolver;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort.LotIntentCoverage;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort.LotIntentRequest;
import com.fabricmanagement.production.execution.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.color.app.SalesColorService;
import com.fabricmanagement.sales.color.app.SalesColorSnapshot;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.lot.app.SalesLotService;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.qualitygrade.app.SalesQualityGradeService;
import com.fabricmanagement.sales.qualitygrade.app.SalesQualityGradeSnapshot;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuoteLineDeliveryStatus;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequest;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestRejectedEvent;
import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestedEvent;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSelectionRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshotCodec;
import com.fabricmanagement.sales.quote.dto.QuoteResponse;
import com.fabricmanagement.sales.quote.dto.QuoteStatusCountsResponse;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteRequest;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteSendRequestRepository;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuoteService {

  private final QuoteRepository quoteRepository;
  private final PricingEngineService pricingEngineService;
  private final SalesProductService catalogService;
  private final DiscountPolicyService policyService;
  private final ExchangeRateService exchangeRateService;
  private final TenantReportingCurrencyPort tenantReportingCurrencyPort;
  private final QuoteApprovalService quoteApprovalService;
  private final TradingPartnerResolver tradingPartnerResolver;
  private final PartnerContactService partnerContactService;
  private final UserDisplayNameResolver userDisplayNameResolver;
  private final QuoteSendRequestRepository quoteSendRequestRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final SalesQualityGradeService salesQualityGradeService;
  private final SalesColorService salesColorService;
  private final SalesLotService salesLotService;
  private final StockUnitSoftHoldPort stockUnitSoftHoldPort;
  private final BatchLotQuantityIntentPort batchLotQuantityIntentPort;

  @Transactional(readOnly = true)
  public Page<Quote> findAll(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<QuoteResponse> findAllResponses(Pageable pageable) {
    return findAllResponses(null, null, pageable);
  }

  @Transactional(readOnly = true)
  public Page<QuoteResponse> findAllResponses(QuoteStatus status, String query, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedQuery = normalizeSearchQuery(query);
    Page<Quote> page = findQuotePage(tenantId, status, normalizedQuery, pageable);
    Map<UUID, String> customerNames =
        tradingPartnerResolver.resolveDisplayNames(
            tenantId,
            page.getContent().stream()
                .map(Quote::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    Map<UUID, String> safeCustomerNames = customerNames != null ? customerNames : Map.of();
    return page.map(quote -> toResponse(quote, safeCustomerNames));
  }

  @Transactional(readOnly = true)
  public QuoteStatusCountsResponse getStatusCounts() {
    UUID tenantId = TenantContext.requireTenantId();
    Map<QuoteStatus, Long> counts = new EnumMap<>(QuoteStatus.class);
    quoteRepository
        .countActiveByStatus(tenantId)
        .forEach(row -> counts.put(row.getStatus(), row.getCount()));
    return QuoteStatusCountsResponse.from(counts);
  }

  private Page<Quote> findQuotePage(
      UUID tenantId, QuoteStatus status, String normalizedQuery, Pageable pageable) {
    if (normalizedQuery == null) {
      return status == null
          ? quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable)
          : quoteRepository.findAllByTenantIdAndStatusAndIsActiveTrue(tenantId, status, pageable);
    }

    String pattern = LikePattern.literalContains(normalizedQuery);
    List<UUID> customerIds =
        tradingPartnerResolver.findCustomerIdsByNameContains(tenantId, normalizedQuery);
    if (customerIds.isEmpty()) {
      return quoteRepository.searchByQuoteNumber(
          tenantId, status, pattern, LikePattern.ESCAPE_CHARACTER, pageable);
    }
    return quoteRepository.searchByQuoteNumberOrCustomerId(
        tenantId, status, pattern, LikePattern.ESCAPE_CHARACTER, customerIds, pageable);
  }

  private String normalizeSearchQuery(String query) {
    if (query == null) {
      return null;
    }
    String trimmed = query.trim();
    return trimmed.length() >= 2 ? trimmed : null;
  }

  @Transactional(readOnly = true)
  public Optional<Quote> findById(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId);
  }

  @Transactional(readOnly = true)
  public Optional<QuoteResponse> findResponseById(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .map(quote -> toResponse(quote, resolveCustomerName(tenantId, quote.getCustomerId())));
  }

  @Transactional
  public Quote createQuote(QuoteCreateRequest req) {
    Quote quote = req.toQuote();
    quote.setTenantId(TenantContext.requireTenantId());
    quote.setStatus(QuoteStatus.DRAFT);
    quote.setRevisionNumber(1);
    return quoteRepository.save(quote);
  }

  @Transactional(readOnly = true)
  public QuoteResponse toResponse(Quote quote) {
    return toResponse(quote, resolveCustomerName(quote.getTenantId(), quote.getCustomerId()));
  }

  @Transactional
  public Quote addQuoteLine(
      UUID quoteId, UUID productId, BigDecimal requestedQty, String unit, BigDecimal offeredPrice) {
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(requestedQty);
    req.setUnit(unit);
    req.setOfferedPrice(offeredPrice);
    return addQuoteLine(quoteId, req);
  }

  @Transactional
  public Quote addQuoteLine(UUID quoteId, AddQuoteLineRequest req) {
    Quote quote = getActiveQuote(quoteId);

    assertEditable(quote, "add lines to");

    UUID productId = req.getProductId();
    SalesProductDto catalogItem = catalogService.getActiveByProductId(productId);
    DiscountPolicy policy = policyService.getActivePolicy(quote.getModuleType());
    SalesQualityGradeSnapshot qualitySnapshot =
        salesQualityGradeService.resolveSnapshot(req.getQualityGradeId());
    SalesColorSnapshot colorSnapshot =
        salesColorService.resolveNewSelectionSnapshot(req.getColorId());
    List<QuoteLineLotSnapshot> lotSnapshots =
        salesLotService.resolveNewSelectionSnapshots(req.getSelectedLots());
    List<LotIntentRequest> lotIntents = buildLotIntentRequests(req.getSelectedLots(), lotSnapshots);
    validateLotIntentTotal(lotIntents, req.getRequestedQty());
    DeliveryDecision delivery =
        resolveDelivery(
            lotIntents,
            checkCoverage(null, lotIntents).covered(),
            req.getDeliveryStatus(),
            req.getDeliveryDate());

    // Evaluate Pricing Zone
    PricingResult pricing =
        pricingEngineService.evaluatePrice(
            catalogItem.getListPrice(),
            req.getOfferedPrice(),
            quote.getEstimatedUnitCost(),
            policy);

    // Create Line
    QuoteLine line = new QuoteLine();
    line.setTenantId(quote.getTenantId());
    line.setProductId(productId);
    line.setRequestedQty(req.getRequestedQty());
    line.setUnit(req.getUnit());
    line.setListPrice(catalogItem.getListPrice());
    line.setOfferedPrice(req.getOfferedPrice());
    line.setCurrency(catalogItem.getCurrency());
    applyQualitySnapshot(line, qualitySnapshot);
    applyColorSnapshot(line, colorSnapshot);
    applyLotSnapshots(line, lotSnapshots);
    line.applyDelivery(delivery.status(), delivery.date(), delivery.covered());
    line.setDiscountRate(pricing.getDiscountRate());
    line.setProfitMargin(pricing.getProfitMargin());
    line.setPriceZone(pricing.getPriceZone());

    quote.addLine(line);
    // Any future line-mutating path must recompute totals before saving.
    recomputeTotals(quote);
    Quote saved = quoteRepository.save(quote);
    replaceSoftHolds(line, lotSnapshots);
    replaceLotIntents(saved, line, lotIntents);
    return saved;
  }

  @Transactional
  public Quote updateQuoteHeader(UUID quoteId, UpdateQuoteRequest req) {
    Quote quote = getActiveQuote(quoteId);
    assertDraftIdentityEditable(quote, req);
    assertEditable(quote, "edit");

    LocalDate previousValidUntil = quote.getValidUntil();
    quote.updateDraftIdentity(req.getCustomerId(), req.getCurrency());
    quote.updateHeader(
        req.getValidUntil(), req.getPaymentTerms(), req.getLeadTimeDays(), req.getNotes());
    recomputeTotals(quote);
    Quote saved = quoteRepository.save(quote);
    if (!Objects.equals(previousValidUntil, saved.getValidUntil())) {
      // Lot-quantity intents snapshot the quote's valid-until as their advisory expiry; keep
      // them in sync so extended quotes don't lose their intents early (QLINE-ATP-1b).
      batchLotQuantityIntentPort.resyncExpiry(saved.getId(), saved.getValidUntil());
    }
    return saved;
  }

  @Transactional
  public Quote updateQuoteLine(UUID quoteId, UUID lineId, UpdateQuoteLineRequest req) {
    Quote quote = getActiveQuote(quoteId);
    assertEditable(quote, "edit lines on");

    QuoteLine line = getQuoteLine(quote, lineId);
    PricingResult pricing = evaluateLinePrice(quote, line, req.getOfferedPrice());
    SalesQualityGradeSnapshot qualitySnapshot =
        salesQualityGradeService.resolveUpdateSnapshot(req.getQualityGradeId(), line);
    SalesColorSnapshot colorSnapshot =
        salesColorService.resolveUpdateSnapshot(req.getColorId(), line);
    List<QuoteLineLotSnapshot> lotSnapshots =
        salesLotService.resolveUpdateSelectionSnapshots(
            req.getSelectedLots(), line.getLotSnapshot());
    List<LotIntentRequest> lotIntents = buildLotIntentRequests(req.getSelectedLots(), lotSnapshots);
    validateLotIntentTotal(lotIntents, req.getRequestedQty());
    DeliveryDecision delivery =
        resolveDelivery(
            lotIntents,
            checkCoverage(lineId, lotIntents).covered(),
            req.getDeliveryStatus(),
            req.getDeliveryDate());

    line.updateEditableFields(req.getRequestedQty(), req.getUnit(), req.getOfferedPrice());
    applyQualitySnapshot(line, qualitySnapshot);
    applyColorSnapshot(line, colorSnapshot);
    applyLotSnapshots(line, lotSnapshots);
    line.applyDelivery(delivery.status(), delivery.date(), delivery.covered());
    line.applyPricing(pricing.getDiscountRate(), pricing.getProfitMargin(), pricing.getPriceZone());
    recomputeTotals(quote);
    Quote saved = quoteRepository.save(quote);
    replaceSoftHolds(line, lotSnapshots);
    replaceLotIntents(saved, line, lotIntents);
    return saved;
  }

  @Transactional
  public Quote removeQuoteLine(UUID quoteId, UUID lineId) {
    Quote quote = getActiveQuote(quoteId);
    assertEditable(quote, "remove lines from");

    QuoteLine line = getQuoteLine(quote, lineId);
    stockUnitSoftHoldPort.releaseHolds(line.getId());
    batchLotQuantityIntentPort.releaseIntents(line.getId());
    quote.removeLine(lineId);
    recomputeTotals(quote);
    return quoteRepository.save(quote);
  }

  @Transactional
  public Quote submitQuote(UUID quoteId) {
    Quote quote = getActiveQuote(quoteId);

    if (quote.getLines().isEmpty()) {
      // Fix #7: domain exception, not raw IllegalStateException
      throw SalesDomainException.invalidQuoteStatus("Cannot submit a quote with no lines");
    }

    // Check zones across all lines
    boolean requiresManagerApproval = false;

    for (QuoteLine line : quote.getLines()) {
      if (line.getPriceZone() == QuotePriceZone.BLOCKED) {
        throw SalesDomainException.invalidPriceZone(
            "Quote contains items in the BLOCKED zone. Cannot submit.");
      }
      if (line.getPriceZone() == QuotePriceZone.MANAGER_APPROVAL) {
        requiresManagerApproval = true;
      }
    }

    if (requiresManagerApproval) {
      quote.setStatus(QuoteStatus.PENDING_APPROVAL);
    } else {
      quote.setStatus(QuoteStatus.APPROVED);
    }

    return quoteRepository.save(quote);
  }

  @Transactional
  public SendQuoteResult sendQuote(UUID quoteId, UUID contactId, boolean callerCanApprove) {
    Quote quote = getActiveQuote(quoteId);
    PartnerContact contact = requireQuoteCustomerContact(quote, contactId);

    if (quote.getStatus() == QuoteStatus.DRAFT || quote.getStatus() == QuoteStatus.EVALUATION) {
      rejectBlockedQuoteForCustomerSend(quote);
      quote = submitQuote(quoteId);
    }

    if (callerCanApprove && quote.getStatus() == QuoteStatus.PENDING_APPROVAL) {
      quote.setStatus(QuoteStatus.APPROVED);
      quote = quoteRepository.save(quote);
    }

    if (quote.getStatus() == QuoteStatus.APPROVED) {
      if (callerCanApprove) {
        return SendQuoteResult.sent(
            quoteApprovalService.generateTokenForQuote(
                quoteId, QuoteApprovalChannel.EMAIL, contact.getEmail(), contact.getId()));
      }
      return SendQuoteResult.awaitingApproval(createPendingSendRequest(quote, contact));
    }

    if (quote.getStatus() == QuoteStatus.PENDING_APPROVAL) {
      return SendQuoteResult.awaitingApproval(createPendingSendRequest(quote, contact));
    }

    throw SalesDomainException.invalidQuoteStatus(
        "Cannot send a quote in " + quote.getStatus() + " status");
  }

  @Transactional
  public SendQuoteResult approveSendRequest(UUID quoteId, UUID requestId) {
    Quote quote = getActiveQuote(quoteId);
    QuoteSendRequest request = getActiveSendRequest(quoteId, requestId);

    if (quote.getStatus() == QuoteStatus.PENDING_APPROVAL) {
      quote.setStatus(QuoteStatus.APPROVED);
      quote = quoteRepository.save(quote);
    }
    if (quote.getStatus() != QuoteStatus.APPROVED) {
      throw SalesDomainException.invalidQuoteStatus(
          "Cannot approve a send request for a quote in " + quote.getStatus() + " status");
    }

    PartnerContact contact = requireQuoteCustomerContact(quote, request.getContactId());
    request.approve(requireCurrentUserId(), Instant.now());
    QuoteSendRequest savedRequest = quoteSendRequestRepository.save(request);

    QuoteApprovalToken token =
        quoteApprovalService.generateTokenForQuote(
            quoteId, request.getChannel(), contact.getEmail(), contact.getId());
    return SendQuoteResult.sent(token, savedRequest);
  }

  @Transactional
  public QuoteSendRequest rejectSendRequest(UUID quoteId, UUID requestId, String decisionNote) {
    Quote quote = getActiveQuote(quoteId);
    QuoteSendRequest request = getActiveSendRequest(quoteId, requestId);

    request.reject(requireCurrentUserId(), Instant.now(), decisionNote);
    quote.setStatus(QuoteStatus.EVALUATION);

    Quote savedQuote = quoteRepository.save(quote);
    QuoteSendRequest savedRequest = quoteSendRequestRepository.save(request);
    eventPublisher.publishEvent(
        new QuoteSendRequestRejectedEvent(
            savedRequest.getTenantId(),
            savedRequest.getId(),
            savedQuote.getId(),
            savedQuote.getQuoteNumber(),
            savedRequest.getRequestedBy(),
            savedRequest.getDecisionNote()));
    return savedRequest;
  }

  @Transactional
  public Quote reviseQuote(UUID quoteId) {
    Quote oldQuote = getActiveQuote(quoteId);

    // Fix #4: Only allow revision from terminal or approval-pending states
    if (oldQuote.getStatus() == QuoteStatus.DRAFT
        || oldQuote.getStatus() == QuoteStatus.EVALUATION) {
      throw SalesDomainException.invalidQuoteStatus(
          "A quote in "
              + oldQuote.getStatus()
              + " status cannot be revised. Submit it first or edit it directly.");
    }

    // 1. Mark old as SUPERSEDED
    oldQuote.setStatus(QuoteStatus.SUPERSEDED);
    quoteApprovalService.expirePendingTokensForQuote(oldQuote.getTenantId(), oldQuote.getId());
    oldQuote.getLines().stream()
        .map(QuoteLine::getId)
        .filter(Objects::nonNull)
        .forEach(batchLotQuantityIntentPort::releaseIntents);
    quoteRepository.save(oldQuote);

    // 2. Clone to new Quote
    Quote newQuote = new Quote();
    newQuote.setTenantId(oldQuote.getTenantId());

    // Fix Quote Number suffix to prevent stacking -R1-R2
    String baseNumber = oldQuote.getQuoteNumber();
    if (baseNumber.contains("-R")) {
      baseNumber = baseNumber.substring(0, baseNumber.lastIndexOf("-R"));
    }
    newQuote.setQuoteNumber(baseNumber + "-R" + oldQuote.getRevisionNumber());

    newQuote.setCustomerId(oldQuote.getCustomerId());
    newQuote.setAssignedToId(oldQuote.getAssignedToId());
    newQuote.setModuleType(oldQuote.getModuleType());
    newQuote.setEstimatedUnitCost(oldQuote.getEstimatedUnitCost());
    newQuote.setCurrency(oldQuote.getCurrency());
    newQuote.setValidUntil(oldQuote.getValidUntil());
    newQuote.setPaymentTerms(oldQuote.getPaymentTerms());
    newQuote.setLeadTimeDays(oldQuote.getLeadTimeDays());
    newQuote.setNotes(oldQuote.getNotes());
    newQuote.setRevisionNumber(oldQuote.getRevisionNumber() + 1);
    newQuote.setParentQuoteId(oldQuote.getId());
    newQuote.setStatus(QuoteStatus.DRAFT);

    // Clone lines — pricing is carried over; salesperson can update offeredPrice on lines
    for (QuoteLine oldLine : oldQuote.getLines()) {
      QuoteLine newLine = new QuoteLine();
      newLine.setTenantId(oldLine.getTenantId());
      newLine.setProductId(oldLine.getProductId());
      newLine.setProductDesc(oldLine.getProductDesc());
      newLine.setRequestedQty(oldLine.getRequestedQty());
      newLine.setUnit(oldLine.getUnit());
      newLine.setListPrice(oldLine.getListPrice());
      newLine.setOfferedPrice(oldLine.getOfferedPrice());
      newLine.setCurrency(oldLine.getCurrency());
      newLine.applyQualityGrade(
          oldLine.getQualityGradeId(),
          oldLine.getQualityGradeCode(),
          oldLine.getQualityGradeName(),
          oldLine.getQualityPriceFactor());
      newLine.applyColor(
          oldLine.getColorId(),
          oldLine.getColorCode(),
          oldLine.getColorName(),
          oldLine.getColorHex());
      newLine.applyLotSnapshot(oldLine.getLotSnapshot());
      newLine.applyDelivery(
          oldLine.getDeliveryStatus(), oldLine.getDeliveryDate(), oldLine.getDeliveryCovered());
      newLine.setDiscountRate(oldLine.getDiscountRate());
      newLine.setProfitMargin(oldLine.getProfitMargin());
      newLine.setPriceZone(oldLine.getPriceZone());
      newLine.setModuleSpecs(oldLine.getModuleSpecs());
      newQuote.addLine(newLine);
    }

    recomputeTotals(newQuote);
    Quote savedRevision = quoteRepository.save(newQuote);
    savedRevision
        .getLines()
        .forEach(
            line ->
                replaceLotIntents(
                    savedRevision,
                    line,
                    buildLotIntentRequestsFromSnapshots(
                        QuoteLineLotSnapshotCodec.fromJson(line.getLotSnapshot()))));
    return savedRevision;
  }

  private Quote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> SalesDomainException.quoteNotFound(quoteId.toString()));
  }

  private void applyQualitySnapshot(QuoteLine line, SalesQualityGradeSnapshot qualitySnapshot) {
    if (qualitySnapshot == null) {
      line.clearQualityGrade();
      return;
    }
    line.applyQualityGrade(
        qualitySnapshot.id(),
        qualitySnapshot.code(),
        qualitySnapshot.name(),
        qualitySnapshot.priceFactor());
  }

  private void applyColorSnapshot(QuoteLine line, SalesColorSnapshot colorSnapshot) {
    if (colorSnapshot == null) {
      line.clearColor();
      return;
    }
    line.applyColor(
        colorSnapshot.id(), colorSnapshot.code(), colorSnapshot.name(), colorSnapshot.colorHex());
  }

  private void applyLotSnapshots(QuoteLine line, List<QuoteLineLotSnapshot> lotSnapshots) {
    line.applyLotSnapshot(QuoteLineLotSnapshotCodec.toJson(lotSnapshots));
  }

  private QuoteSendRequest getActiveSendRequest(UUID quoteId, UUID requestId) {
    UUID tenantId = TenantContext.requireTenantId();
    QuoteSendRequest request =
        quoteSendRequestRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, requestId)
            .orElseThrow(() -> SalesDomainException.quoteSendRequestNotFound(requestId.toString()));
    if (!quoteId.equals(request.getQuoteId())) {
      throw SalesDomainException.quoteSendRequestNotFound(requestId.toString());
    }
    return request;
  }

  private QuoteSendRequest createPendingSendRequest(Quote quote, PartnerContact contact) {
    UUID tenantId = quote.getTenantId();
    quoteSendRequestRepository
        .findPendingByTenantIdAndQuoteId(tenantId, quote.getId())
        .ifPresent(
            existing -> {
              throw SalesDomainException.quoteSendRequestAlreadyPending(
                  "Quote already has a pending send request.");
            });

    QuoteSendRequest request =
        QuoteSendRequest.create(
            tenantId,
            quote.getId(),
            contact.getId(),
            QuoteApprovalChannel.EMAIL,
            requireCurrentUserId(),
            Instant.now());
    try {
      QuoteSendRequest saved = quoteSendRequestRepository.save(request);
      eventPublisher.publishEvent(
          new QuoteSendRequestedEvent(
              saved.getTenantId(),
              saved.getId(),
              quote.getId(),
              quote.getQuoteNumber(),
              saved.getRequestedBy()));
      return saved;
    } catch (DataIntegrityViolationException ex) {
      throw SalesDomainException.quoteSendRequestAlreadyPending(
          "Quote already has a pending send request.");
    }
  }

  private QuoteResponse toResponse(Quote quote, Map<UUID, String> customerNames) {
    return QuoteResponse.from(quote, customerNames.get(quote.getCustomerId()));
  }

  private QuoteResponse toResponse(Quote quote, String customerName) {
    return QuoteResponse.from(quote, customerName);
  }

  private PartnerContact requireQuoteCustomerContact(Quote quote, UUID contactId) {
    PartnerContact contact =
        partnerContactService.requireActiveContact(quote.getTenantId(), contactId);
    if (!quote.getCustomerId().equals(contact.getPartner().getId())) {
      throw SalesDomainException.invalidQuoteRecipientContact(
          "Quote recipient contact must belong to the quote customer");
    }
    if (contact.getEmail() == null || contact.getEmail().isBlank()) {
      throw SalesDomainException.invalidQuoteRecipientContact(
          "Quote recipient contact must have an email address");
    }
    return contact;
  }

  private String resolveCustomerName(UUID tenantId, UUID customerId) {
    if (tenantId == null || customerId == null) {
      return null;
    }
    Map<UUID, String> customerNames =
        tradingPartnerResolver.resolveDisplayNames(tenantId, List.of(customerId));
    return customerNames != null ? customerNames.get(customerId) : null;
  }

  private void assertEditable(Quote quote, String action) {
    if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.EVALUATION) {
      throw SalesDomainException.invalidQuoteStatus(
          "Cannot " + action + " a quote in " + quote.getStatus() + " status");
    }
  }

  private void assertDraftIdentityEditable(Quote quote, UpdateQuoteRequest req) {
    if (req.getCustomerId() == null && req.getCurrency() == null) {
      return;
    }
    if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.EVALUATION) {
      throw SalesDomainException.quoteDraftIdentityLocked(
          "Quote customer and currency can only be changed on draft or evaluation quotes.");
    }
    if (!quote.getLines().isEmpty()) {
      throw SalesDomainException.quoteDraftIdentityLocked(
          "Quote customer and currency are locked after the first line is added.");
    }
  }

  private void rejectBlockedQuoteForCustomerSend(Quote quote) {
    boolean hasBlockedLine =
        quote.getLines().stream().anyMatch(line -> line.getPriceZone() == QuotePriceZone.BLOCKED);
    if (hasBlockedLine) {
      throw SalesDomainException.needsInternalApproval(
          "Quote needs internal approval before it can be sent to the customer.");
    }
  }

  private UUID requireCurrentUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw SalesDomainException.invalidQuoteSendRequestDecision(
          "Authenticated user context is required for quote send approval workflow.");
    }
    return userId;
  }

  private QuoteLine getQuoteLine(Quote quote, UUID lineId) {
    return quote
        .findLine(lineId)
        .orElseThrow(() -> SalesDomainException.quoteNotFound("line " + lineId));
  }

  private PricingResult evaluateLinePrice(Quote quote, QuoteLine line, BigDecimal offeredPrice) {
    DiscountPolicy policy = policyService.getActivePolicy(quote.getModuleType());
    return pricingEngineService.evaluatePrice(
        line.getListPrice(), offeredPrice, quote.getEstimatedUnitCost(), policy);
  }

  private void recomputeTotals(Quote quote) {
    LocalDate documentDate = docDate(quote);
    String reportingCurrency =
        tenantReportingCurrencyPort.getReportingCurrency(quote.getTenantId());
    String rawHeaderCurrency = quote.getCurrency();
    final String headerCurrency =
        (rawHeaderCurrency == null || rawHeaderCurrency.isBlank())
            ? reportingCurrency
            : rawHeaderCurrency;

    BigDecimal nativeTotal =
        quote.getLines().stream()
            .map(
                line ->
                    convertLineTotalToHeader(
                        quote.getTenantId(), line, headerCurrency, documentDate))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    quote.setTotalAmount(Money.of(nativeTotal, headerCurrency));
    quote.setReportingTotal(
        convertMoney(
            quote.getTenantId(), nativeTotal, headerCurrency, reportingCurrency, documentDate));
  }

  private void replaceSoftHolds(QuoteLine line, List<QuoteLineLotSnapshot> lotSnapshots) {
    if (line.getId() == null) {
      return;
    }
    List<UUID> stockUnitIds =
        (lotSnapshots == null ? List.<QuoteLineLotSnapshot>of() : lotSnapshots)
            .stream()
                .flatMap(lot -> lot.pieces().stream())
                .map(piece -> piece.stockUnitId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    stockUnitSoftHoldPort.replaceHolds(line.getId(), stockUnitIds);
  }

  private List<LotIntentRequest> buildLotIntentRequests(
      List<QuoteLineLotSelectionRequest> selections, List<QuoteLineLotSnapshot> lotSnapshots) {
    if (selections == null || selections.isEmpty()) {
      return List.of();
    }
    Map<UUID, QuoteLineLotSelectionRequest> selectionsByLot =
        selections.stream()
            .filter(selection -> selection.lotId() != null)
            .collect(
                Collectors.toMap(
                    QuoteLineLotSelectionRequest::lotId,
                    selection -> selection,
                    (left, right) -> right));
    return (lotSnapshots == null ? List.<QuoteLineLotSnapshot>of() : lotSnapshots)
        .stream()
            .map(
                snapshot -> {
                  QuoteLineLotSelectionRequest selection = selectionsByLot.get(snapshot.lotId());
                  BigDecimal quantity =
                      selection != null && selection.quantity() != null
                          ? selection.quantity()
                          : snapshot.derivedQuantity();
                  return new LotIntentRequest(snapshot.lotId(), quantity, snapshot.unit());
                })
            .toList();
  }

  private List<LotIntentRequest> buildLotIntentRequestsFromSnapshots(
      List<QuoteLineLotSnapshot> lotSnapshots) {
    return (lotSnapshots == null ? List.<QuoteLineLotSnapshot>of() : lotSnapshots)
        .stream()
            .map(
                snapshot ->
                    new LotIntentRequest(
                        snapshot.lotId(), snapshot.derivedQuantity(), snapshot.unit()))
            .toList();
  }

  private void validateLotIntentTotal(
      List<LotIntentRequest> lotIntents, BigDecimal requestedQuantity) {
    if (lotIntents == null || lotIntents.isEmpty()) {
      return;
    }
    BigDecimal total =
        lotIntents.stream()
            .map(LotIntentRequest::quantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(requestedQuantity) != 0) {
      throw SalesDomainException.lotIntentQuantityMismatch(
          "Selected lot quantities must equal the quote line requested quantity.");
    }
  }

  private LotIntentCoverage checkCoverage(UUID quoteLineId, List<LotIntentRequest> lotIntents) {
    if (lotIntents == null || lotIntents.isEmpty()) {
      return new LotIntentCoverage(true);
    }
    return batchLotQuantityIntentPort.checkCoverage(quoteLineId, lotIntents);
  }

  private DeliveryDecision resolveDelivery(
      List<LotIntentRequest> lotIntents,
      boolean covered,
      QuoteLineDeliveryStatus requestedStatus,
      LocalDate deliveryDate) {
    if (lotIntents == null || lotIntents.isEmpty()) {
      return new DeliveryDecision(requestedStatus, deliveryDate, null);
    }
    QuoteLineDeliveryStatus status = requestedStatus;
    if (!covered && status == null) {
      throw SalesDomainException.deliveryStatusRequired(
          "Delivery status is required when selected lot free stock does not cover the line.");
    }
    if (!covered && status == QuoteLineDeliveryStatus.FROM_STOCK) {
      throw SalesDomainException.deliveryStatusRequired(
          "Delivery status must be TO_BE_CONFIRMED, FROM_PRODUCTION, or STOCK_OVERRIDE when selected lot free stock does not cover the line.");
    }
    if (covered && status == null && deliveryDate != null) {
      status = QuoteLineDeliveryStatus.FROM_STOCK;
    }
    Boolean coveredSnapshot = status == QuoteLineDeliveryStatus.STOCK_OVERRIDE ? false : covered;
    return new DeliveryDecision(status, deliveryDate, coveredSnapshot);
  }

  private void replaceLotIntents(Quote quote, QuoteLine line, List<LotIntentRequest> lotIntents) {
    if (line.getId() == null) {
      return;
    }
    batchLotQuantityIntentPort.replaceIntents(
        quote.getId(),
        quote.getQuoteNumber(),
        line.getId(),
        quote.getAssignedToId(),
        resolveMarketerName(quote),
        quote.getValidUntil(),
        lotIntents);
  }

  private String resolveMarketerName(Quote quote) {
    if (quote.getTenantId() == null || quote.getAssignedToId() == null) {
      return null;
    }
    return userDisplayNameResolver
        .resolveDisplayName(quote.getTenantId(), quote.getAssignedToId())
        .orElse(null);
  }

  private record DeliveryDecision(
      QuoteLineDeliveryStatus status, LocalDate date, Boolean covered) {}

  private BigDecimal convertLineTotalToHeader(
      UUID tenantId, QuoteLine line, String headerCurrency, LocalDate documentDate) {
    BigDecimal lineAmount = line.lineTotal();
    String lineCurrency = line.getCurrency();
    if (lineCurrency == null || lineCurrency.isBlank()) {
      lineCurrency = headerCurrency;
    }
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
    if (amount.compareTo(BigDecimal.ZERO) == 0) {
      return ConvertedMoney.of(
          BigDecimal.ZERO, fromCurrency, BigDecimal.ZERO, toCurrency, BigDecimal.ONE, documentDate);
    }

    try {
      return exchangeRateService.convert(tenantId, amount, fromCurrency, toCurrency, documentDate);
    } catch (ExchangeRateRequiredException ex) {
      throw SalesDomainException.exchangeRateRequired(
          String.format(
              "No exchange rate for %s->%s on %s; seed a rate before saving this quote",
              fromCurrency, toCurrency, documentDate),
          ex);
    }
  }

  private LocalDate docDate(Quote quote) {
    Instant dateSource = quote.getCreatedAt();
    return dateSource != null ? dateSource.atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now();
  }
}
