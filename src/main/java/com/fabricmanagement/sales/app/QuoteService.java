package com.fabricmanagement.sales.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.api.QuoteCreateRequest;
import com.fabricmanagement.sales.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.domain.catalog.ProductCatalog;
import com.fabricmanagement.sales.domain.exception.SalesDomainException;
import com.fabricmanagement.sales.domain.policy.DiscountPolicy;
import com.fabricmanagement.sales.domain.quote.Quote;
import com.fabricmanagement.sales.domain.quote.QuoteLine;
import com.fabricmanagement.sales.domain.quote.QuotePriceZone;
import com.fabricmanagement.sales.domain.quote.QuoteStatus;
import com.fabricmanagement.sales.infra.repository.QuoteRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuoteService {

  private final QuoteRepository quoteRepository;
  private final PricingEngineService pricingEngineService;
  private final ProductCatalogService catalogService;
  private final DiscountPolicyService policyService;

  @Transactional
  public Quote createQuote(QuoteCreateRequest req) {
    Quote quote = req.toQuote();
    quote.setTenantId(TenantContext.getCurrentTenantId());
    quote.setStatus(QuoteStatus.DRAFT);
    quote.setRevisionNumber(1);
    return quoteRepository.save(quote);
  }

  @Transactional
  public Quote addQuoteLine(
      UUID quoteId,
      UUID materialId,
      BigDecimal requestedQty,
      String unit,
      BigDecimal offeredPrice) {
    Quote quote = getActiveQuote(quoteId);

    if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.EVALUATION) {
      throw SalesDomainException.invalidQuoteStatus(
          "Cannot add lines to a quote in " + quote.getStatus() + " status");
    }

    ProductCatalog catalogItem = catalogService.getActiveByMaterialId(materialId);
    DiscountPolicy policy = policyService.getActivePolicy(quote.getModuleType());

    // Evaluate Pricing Zone
    PricingResult pricing =
        pricingEngineService.evaluatePrice(
            catalogItem.getListPrice(), offeredPrice, quote.getEstimatedUnitCost(), policy);

    // Create Line
    QuoteLine line = new QuoteLine();
    line.setTenantId(quote.getTenantId());
    line.setMaterialId(materialId);
    line.setRequestedQty(requestedQty);
    line.setUnit(unit);
    line.setListPrice(catalogItem.getListPrice());
    line.setOfferedPrice(offeredPrice);
    line.setDiscountRate(pricing.getDiscountRate());
    line.setProfitMargin(pricing.getProfitMargin());
    line.setPriceZone(pricing.getPriceZone());

    quote.addLine(line);
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
    newQuote.setQuoteNumber(oldQuote.getQuoteNumber() + "-R" + oldQuote.getRevisionNumber());
    newQuote.setCustomerId(oldQuote.getCustomerId());
    newQuote.setAssignedToId(oldQuote.getAssignedToId());
    newQuote.setModuleType(oldQuote.getModuleType());
    newQuote.setEstimatedUnitCost(oldQuote.getEstimatedUnitCost());
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
      newLine.setMaterialId(oldLine.getMaterialId());
      newLine.setProductDesc(oldLine.getProductDesc());
      newLine.setRequestedQty(oldLine.getRequestedQty());
      newLine.setUnit(oldLine.getUnit());
      newLine.setListPrice(oldLine.getListPrice());
      newLine.setOfferedPrice(oldLine.getOfferedPrice());
      newLine.setDiscountRate(oldLine.getDiscountRate());
      newLine.setProfitMargin(oldLine.getProfitMargin());
      newLine.setPriceZone(oldLine.getPriceZone());
      newLine.setModuleSpecs(oldLine.getModuleSpecs());
      newQuote.addLine(newLine);
    }

    return quoteRepository.save(newQuote);
  }

  private Quote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> SalesDomainException.quoteNotFound(quoteId.toString()));
  }
}
