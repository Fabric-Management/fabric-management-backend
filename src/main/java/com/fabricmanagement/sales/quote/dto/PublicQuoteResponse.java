package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.dto.ConvertedMoneyDto;
import com.fabricmanagement.common.dto.MoneyDto;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
@Schema(description = "Customer-facing quote projection without internal pricing fields")
public class PublicQuoteResponse {

  private final UUID id;
  private final String quoteNumber;
  private final String moduleType;
  private final QuoteStatus status;
  private final String currency;
  private final MoneyDto totalAmount;
  private final ConvertedMoneyDto reportingTotal;
  private final LocalDate validUntil;
  private final String paymentTerms;

  /**
   * @deprecated Superseded by per-line {@code QuoteLine.deliveryStatus}/{@code deliveryDate}
   *     (QLINE-ATP-1); retained for historical data, no longer written by the FE. See
   *     docs/sales/tickets/QLINE-LEADTIME-1-remove-quote-header-lead-time.md.
   */
  @Deprecated private final Integer leadTimeDays;

  private final String notes;
  private final Integer revisionNumber;
  private final List<PublicQuoteLineResponse> lines;

  private PublicQuoteResponse(Quote quote) {
    this.id = quote.getId();
    this.quoteNumber = quote.getQuoteNumber();
    this.moduleType = quote.getModuleType();
    this.status = quote.getStatus();
    this.currency = quote.getCurrency();
    this.totalAmount = MoneyDto.from(quote.getTotalAmount());
    this.reportingTotal = toDto(quote.getReportingTotal());
    this.validUntil = quote.getValidUntil();
    this.paymentTerms = quote.getPaymentTerms();
    this.leadTimeDays = quote.getLeadTimeDays();
    this.notes = quote.getNotes();
    this.revisionNumber = quote.getRevisionNumber();
    this.lines = quote.getLines().stream().map(PublicQuoteLineResponse::from).toList();
  }

  public static PublicQuoteResponse from(Quote quote) {
    return new PublicQuoteResponse(quote);
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
