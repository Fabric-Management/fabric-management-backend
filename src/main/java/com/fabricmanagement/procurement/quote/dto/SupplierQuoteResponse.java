package com.fabricmanagement.procurement.quote.dto;

import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** Fix #1 — API response DTO: internal alanları sızdırmadan temiz bir kontrakt sunar. */
@Value
@Builder
public class SupplierQuoteResponse {

  UUID id;
  String quoteNumber;
  UUID rfqId;
  UUID tradingPartnerId;
  SupplierQuoteStatus status;
  LocalDate validUntil;
  String currency;
  String paymentTerms;
  Integer leadTimeDays;
  QuoteEntryMethod entryMethod;
  String notes;
  Instant submittedAt;
  List<QuoteLineResponse> lines;

  @Value
  @Builder
  public static class QuoteLineResponse {
    UUID id;
    UUID rfqLineId;
    BigDecimal unitPrice;
    String currency;
    BigDecimal qty;
    String unit;
    String volumeDiscounts;
    String notes;
  }

  /** Transaction içinde lines yüklenmişken çağrılmalı — LazyInitializationException önlenir. */
  public static SupplierQuoteResponse from(SupplierQuote quote) {
    List<QuoteLineResponse> lineResps =
        quote.getLines().stream()
            .map(
                l ->
                    QuoteLineResponse.builder()
                        .id(l.getId())
                        .rfqLineId(l.getRfqLineId())
                        .unitPrice(l.getUnitPrice())
                        .currency(l.getCurrency())
                        .qty(l.getQty())
                        .unit(l.getUnit())
                        .volumeDiscounts(l.getVolumeDiscounts())
                        .notes(l.getNotes())
                        .build())
            .toList();

    return SupplierQuoteResponse.builder()
        .id(quote.getId())
        .quoteNumber(quote.getQuoteNumber())
        .rfqId(quote.getRfqId())
        .tradingPartnerId(quote.getTradingPartnerId())
        .status(quote.getStatus())
        .validUntil(quote.getValidUntil())
        .currency(quote.getCurrency())
        .paymentTerms(quote.getPaymentTerms())
        .leadTimeDays(quote.getLeadTimeDays())
        .entryMethod(quote.getEntryMethod())
        .notes(quote.getNotes())
        .submittedAt(quote.getSubmittedAt())
        .lines(lineResps)
        .build();
  }
}
