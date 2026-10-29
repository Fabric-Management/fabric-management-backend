package com.fabricmanagement.finance.collection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Amounts are exact decimal strings. Null means the cut cannot establish a reliable balance: a
 * nullable field is always written (null), never omitted, so every nullable field is required in
 * the contract.
 */
public final class CollectionReportDtos {
  private CollectionReportDtos() {}

  @Schema(
      name = "FinanceCollectionReportRow",
      requiredProperties = {
        "id",
        "orderId",
        "partnerId",
        "customerLabel",
        "responsibleUserId",
        "responsibleDisplayName",
        "currency",
        "method",
        "expectedDate",
        "chequePaymentDate",
        "plannedAmount",
        "recordedCashAmount",
        "outstandingAmount",
        "receivedChequeAmount",
        "missingInstrumentAmount",
        "refundPendingAmount",
        "cancelled",
        "sourceStale",
        "sourceMissing",
        "sourceObservedAt",
        "buckets",
        "warnings"
      })
  public record Row(
      UUID id,
      UUID orderId,
      UUID partnerId,
      @Schema(nullable = true) String customerLabel,
      @Schema(nullable = true) UUID responsibleUserId,
      @Schema(nullable = true) String responsibleDisplayName,
      String currency,
      String method,
      @Schema(nullable = true) LocalDate expectedDate,
      @Schema(nullable = true) LocalDate chequePaymentDate,
      @Schema(type = "string") String plannedAmount,
      @Schema(type = "string", nullable = true) String recordedCashAmount,
      @Schema(type = "string", nullable = true) String outstandingAmount,
      @Schema(type = "string", nullable = true) String receivedChequeAmount,
      @Schema(type = "string", nullable = true) String missingInstrumentAmount,
      @Schema(
              type = "string",
              description = "Cash owed back to the customer by refunds still open at the cut")
          String refundPendingAmount,
      boolean cancelled,
      boolean sourceStale,
      boolean sourceMissing,
      @Schema(nullable = true) Instant sourceObservedAt,
      List<String> buckets,
      List<String> warnings) {}

  @Schema(
      name = "FinanceCollectionReportCurrency",
      requiredProperties = {
        "currency",
        "rows",
        "unknownBalanceRows",
        "plannedAmount",
        "recordedCashAmount",
        "outstandingAmount",
        "receivedChequeAmount",
        "missingInstrumentAmount",
        "refundPendingAmount"
      })
  public record Currency(
      String currency,
      long rows,
      long unknownBalanceRows,
      @Schema(type = "string") String plannedAmount,
      @Schema(type = "string", nullable = true) String recordedCashAmount,
      @Schema(type = "string", nullable = true) String outstandingAmount,
      @Schema(type = "string", nullable = true) String receivedChequeAmount,
      @Schema(type = "string", nullable = true) String missingInstrumentAmount,
      @Schema(type = "string") String refundPendingAmount) {}

  @Schema(
      name = "FinanceCollectionReport",
      requiredProperties = {
        "asOfDate",
        "knowledgeCutoff",
        "generatedAt",
        "tenantTimeZone",
        "content",
        "page",
        "size",
        "totalElements",
        "currencies",
        "oldestSourceCheckedAt",
        "legacyOrderCount",
        "warnings"
      })
  public record Report(
      LocalDate asOfDate,
      Instant knowledgeCutoff,
      Instant generatedAt,
      String tenantTimeZone,
      List<Row> content,
      int page,
      int size,
      long totalElements,
      List<Currency> currencies,
      @Schema(
              nullable = true,
              description =
                  "Oldest current source check among filtered rows when known by the cutoff; null"
                      + " if incomplete or historical check evidence unavailable")
          Instant oldestSourceCheckedAt,
      @Schema(
              description =
                  "Coverage of orders without a structured plan cannot be established by this"
                      + " ledger. Null is unknown, never zero.",
              nullable = true)
          Long legacyOrderCount,
      List<String> warnings) {}
}
