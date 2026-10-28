package com.fabricmanagement.finance.collection.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Finance evidence projection, independent of HTTP and workflow state. */
public final class CollectionReportFacts {
  private CollectionReportFacts() {}

  public record Row(
      UUID id,
      UUID orderId,
      UUID partnerId,
      String customerLabel,
      UUID responsibleUserId,
      String responsibleDisplayName,
      String currency,
      String method,
      LocalDate expectedDate,
      LocalDate chequePaymentDate,
      String plannedAmount,
      String recordedCashAmount,
      String outstandingAmount,
      String receivedChequeAmount,
      String missingInstrumentAmount,
      String refundPendingAmount,
      boolean cancelled,
      boolean sourceStale,
      boolean sourceMissing,
      Instant sourceObservedAt,
      List<String> buckets,
      List<String> warnings) {}

  public record Currency(
      String currency,
      long rows,
      long unknownBalanceRows,
      String plannedAmount,
      String recordedCashAmount,
      String outstandingAmount,
      String receivedChequeAmount,
      String missingInstrumentAmount,
      String refundPendingAmount) {}
}
