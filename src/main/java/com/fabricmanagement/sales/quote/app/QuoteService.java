package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteRequest;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

  @Transactional(readOnly = true)
  public Page<Quote> findAll(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable);
  }

  @Transactional(readOnly = true)
  public Optional<Quote> findById(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId);
  }

  @Transactional
  public Quote createQuote(QuoteCreateRequest req) {
    Quote quote = req.toQuote();
    quote.setTenantId(TenantContext.requireTenantId());
    quote.setStatus(QuoteStatus.DRAFT);
    quote.setRevisionNumber(1);
    return quoteRepository.save(quote);
  }

  @Transactional
  public Quote addQuoteLine(
      UUID quoteId, UUID productId, BigDecimal requestedQty, String unit, BigDecimal offeredPrice) {
    Quote quote = getActiveQuote(quoteId);

    assertEditable(quote, "add lines to");

    SalesProductDto catalogItem = catalogService.getActiveByProductId(productId);
    DiscountPolicy policy = policyService.getActivePolicy(quote.getModuleType());

    // Evaluate Pricing Zone
    PricingResult pricing =
        pricingEngineService.evaluatePrice(
            catalogItem.getListPrice(), offeredPrice, quote.getEstimatedUnitCost(), policy);

    // Create Line
    QuoteLine line = new QuoteLine();
    line.setTenantId(quote.getTenantId());
    line.setProductId(productId);
    line.setRequestedQty(requestedQty);
    line.setUnit(unit);
    line.setListPrice(catalogItem.getListPrice());
    line.setOfferedPrice(offeredPrice);
    line.setCurrency(catalogItem.getCurrency());
    line.setDiscountRate(pricing.getDiscountRate());
    line.setProfitMargin(pricing.getProfitMargin());
    line.setPriceZone(pricing.getPriceZone());

    quote.addLine(line);
    // Any future line-mutating path must recompute totals before saving.
    recomputeTotals(quote);
    return quoteRepository.save(quote);
  }

  @Transactional
  public Quote updateQuoteHeader(UUID quoteId, UpdateQuoteRequest req) {
    Quote quote = getActiveQuote(quoteId);
    assertEditable(quote, "edit");

    quote.updateHeader(
        req.getValidUntil(), req.getPaymentTerms(), req.getLeadTimeDays(), req.getNotes());
    recomputeTotals(quote);
    return quoteRepository.save(quote);
  }

  @Transactional
  public Quote updateQuoteLine(UUID quoteId, UUID lineId, UpdateQuoteLineRequest req) {
    Quote quote = getActiveQuote(quoteId);
    assertEditable(quote, "edit lines on");

    QuoteLine line = getQuoteLine(quote, lineId);
    PricingResult pricing = evaluateLinePrice(quote, line, req.getOfferedPrice());

    line.updateEditableFields(req.getRequestedQty(), req.getUnit(), req.getOfferedPrice());
    line.applyPricing(pricing.getDiscountRate(), pricing.getProfitMargin(), pricing.getPriceZone());
    recomputeTotals(quote);
    return quoteRepository.save(quote);
  }

  @Transactional
  public Quote removeQuoteLine(UUID quoteId, UUID lineId) {
    Quote quote = getActiveQuote(quoteId);
    assertEditable(quote, "remove lines from");

    if (!quote.removeLine(lineId)) {
      throw SalesDomainException.quoteNotFound("line " + lineId);
    }
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
      newLine.setDiscountRate(oldLine.getDiscountRate());
      newLine.setProfitMargin(oldLine.getProfitMargin());
      newLine.setPriceZone(oldLine.getPriceZone());
      newLine.setModuleSpecs(oldLine.getModuleSpecs());
      newQuote.addLine(newLine);
    }

    recomputeTotals(newQuote);
    return quoteRepository.save(newQuote);
  }

  private Quote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> SalesDomainException.quoteNotFound(quoteId.toString()));
  }

  private void assertEditable(Quote quote, String action) {
    if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.EVALUATION) {
      throw SalesDomainException.invalidQuoteStatus(
          "Cannot " + action + " a quote in " + quote.getStatus() + " status");
    }
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
