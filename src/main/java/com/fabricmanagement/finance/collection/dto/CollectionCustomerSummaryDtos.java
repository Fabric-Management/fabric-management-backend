package com.fabricmanagement.finance.collection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CollectionCustomerSummaryDtos {
  private CollectionCustomerSummaryDtos() {}

  @Schema(name = "FinanceCollectionSummaryCoverage", enumAsRef = true)
  public enum Coverage {
    RECORDED_PLANS,
    RESTRICTED_RECORDED_PLANS
  }

  @Schema(name = "FinanceCollectionSummaryNextAction", enumAsRef = true)
  public enum NextAction {
    PAYMENT_DUE,
    CHEQUE_DELIVERY_DUE,
    REVIEW_PAYMENT_METHOD,
    NONE
  }

  @Schema(name = "FinanceCollectionCustomerSummaryDto")
  public record Summary(
      UUID partnerId,
      LocalDate asOfDate,
      String timeZone,
      Instant calculatedAt,
      Coverage coverage,
      boolean sourceDiscoveryStale,
      List<Currency> currencies) {}

  @Schema(name = "FinanceCollectionCustomerSummaryCurrencyDto")
  public record Currency(
      String currency,
      long undatedOpenInstalments,
      long unavailablePlanInstalments,
      @Schema(nullable = true) String paymentDue,
      @Schema(nullable = true) String chequeDeliveryDue,
      long awaitingBankResult,
      @Schema(nullable = true) String unpaidInstalments,
      @Schema(nullable = true) String appliedHeldCheques,
      @Schema(nullable = true) String unallocatedPayments,
      @Schema(nullable = true) String unallocatedCheques,
      List<Instalment> instalments,
      List<Cheque> presentedCheques,
      List<Cheque> chequesAvailableToOffer) {}

  @Schema(name = "FinanceCollectionSummaryInstalmentDto")
  public record Instalment(
      UUID id,
      @Schema(nullable = true) String orderNumber,
      @Schema(nullable = true) LocalDate expectedDate,
      String agreedAmount,
      @Schema(nullable = true) String outstandingAmount,
      @Schema(nullable = true) String missingChequeAmount,
      NextAction nextAction,
      boolean canRecordChequeOffer,
      List<Offer> offers) {}

  @Schema(name = "FinanceCollectionSummaryChequeDto")
  public record Cheque(UUID id, String reference, LocalDate bankDueDate, String amount) {}

  @Schema(name = "FinanceCollectionChequeOfferDto")
  public record Offer(UUID chequeId, UUID instalmentId, UUID actorId, Instant recordedAt) {}

  @Schema(name = "FinanceCollectionRecordChequeOfferRequest")
  public record RecordOffer(@NotNull UUID instalmentId) {}
}
