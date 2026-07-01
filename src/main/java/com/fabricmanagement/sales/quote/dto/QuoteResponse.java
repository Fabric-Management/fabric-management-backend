package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.dto.ConvertedMoneyDto;
import com.fabricmanagement.common.dto.MoneyDto;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteResponse {

  private final UUID id;
  private final String quoteNumber;
  private final UUID customerId;
  private final UUID assignedToId;
  private final String moduleType;
  private final QuoteStatus status;
  private final BigDecimal estimatedUnitCost;
  private final String currency;
  private final MoneyDto totalAmount;
  private final ConvertedMoneyDto reportingTotal;
  private final LocalDate validUntil;
  private final String paymentTerms;
  private final Integer leadTimeDays;
  private final String notes;
  private final Integer revisionNumber;
  private final UUID parentQuoteId;
  private final Instant createdAt;
  private final List<QuoteLineResponse> lines;

  private QuoteResponse(Quote quote) {
    this.id = quote.getId();
    this.quoteNumber = quote.getQuoteNumber();
    this.customerId = quote.getCustomerId();
    this.assignedToId = quote.getAssignedToId();
    this.moduleType = quote.getModuleType();
    this.status = quote.getStatus();
    this.estimatedUnitCost = quote.getEstimatedUnitCost();
    this.currency = quote.getCurrency();
    this.totalAmount = MoneyDto.from(quote.getTotalAmount());
    this.reportingTotal = toDto(quote.getReportingTotal());
    this.validUntil = quote.getValidUntil();
    this.paymentTerms = quote.getPaymentTerms();
    this.leadTimeDays = quote.getLeadTimeDays();
    this.notes = quote.getNotes();
    this.revisionNumber = quote.getRevisionNumber();
    this.parentQuoteId = quote.getParentQuoteId();
    this.createdAt = quote.getCreatedAt();
    this.lines = quote.getLines().stream().map(QuoteLineResponse::from).toList();
  }

  public static QuoteResponse from(Quote quote) {
    return new QuoteResponse(quote);
  }

  private static ConvertedMoneyDto toDto(ConvertedMoney money) {
    if (money == null) {
      return null;
    }
    return ConvertedMoneyDto.builder()
        .originalAmount(money.getOriginalAmount())
        .originalCurrency(money.getOriginalCurrency())
        .convertedAmount(money.getConvertedAmount())
        .convertedCurrency(money.getConvertedCurrency())
        .exchangeRate(money.getExchangeRate())
        .rateDate(money.getRateDate())
        .build();
  }
}
