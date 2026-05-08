package com.fabricmanagement.procurement.quote.dto;

import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteModuleType;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.domain.specs.SupplierQuoteSpecs;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** API response DTO for SupplierQuote. */
@Schema(description = "Supplier Quote Response")
@Value
@Builder
public class SupplierQuoteResponse {

  @Schema(description = "Internal ID")
  UUID id;

  @Schema(description = "Human readable UID")
  String uid;

  @Schema(description = "Quote Number")
  String quoteNumber;

  @Schema(description = "RFQ ID")
  UUID rfqId;

  @Schema(description = "Trading Partner ID")
  UUID tradingPartnerId;

  @Schema(description = "Module Type")
  SupplierQuoteModuleType moduleType;

  @Schema(description = "Current Status")
  SupplierQuoteStatus status;

  @Schema(description = "Valid Until Date")
  LocalDate validUntil;

  @Schema(description = "Currency Code")
  String currency;

  @Schema(description = "Payment Terms")
  String paymentTerms;

  @Schema(description = "Lead Time in Days")
  Integer leadTimeDays;

  @Schema(description = "Entry Method")
  QuoteEntryMethod entryMethod;

  @Schema(description = "Notes")
  String notes;

  @Schema(description = "Submission Timestamp")
  Instant submittedAt;

  @Schema(description = "Total Amount of the Quote")
  BigDecimal totalAmount;

  @Schema(description = "Quote Lines")
  List<QuoteLineResponse> lines;

  @Value
  @Builder
  @Schema(description = "Supplier Quote Line Response")
  public static class QuoteLineResponse {
    @Schema(description = "Line ID")
    UUID id;

    @Schema(description = "RFQ Line ID")
    UUID rfqLineId;

    @Schema(description = "Unit Price")
    BigDecimal unitPrice;

    @Schema(description = "Currency")
    String currency;

    @Schema(description = "Quantity")
    BigDecimal qty;

    @Schema(description = "Unit of Measure")
    String unit;

    @Schema(description = "Module Specs")
    SupplierQuoteSpecs moduleSpecs;

    @Schema(description = "Total Line Amount")
    BigDecimal lineTotal;

    @Schema(description = "Volume Discounts")
    Map<String, Object> volumeDiscounts;

    @Schema(description = "Line Notes")
    String notes;
  }
}
