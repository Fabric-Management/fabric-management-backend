package com.fabricmanagement.finance.collection.dto;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class CollectionDtos {
  private CollectionDtos() {}

  @Schema(
      name = "FinanceCollectionConflict",
      requiredProperties = {"code", "detail"})
  public record Conflict(
      String code, String detail, @Schema(nullable = true) Long currentVersion) {}

  @Schema(
      name = "FinanceCollectionCapability",
      requiredProperties = {"action", "allowed", "needsApproval"})
  public record Capability(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {
                "MATCH_PAYMENT",
                "RECORD_RECEIPT",
                "RECEIVE_CHEQUE",
                "REVERSE_MATCH",
                "RESOLVE_OVER_SETTLEMENT",
                "REFUND_OVER_SETTLEMENT",
                "SETTLE_REFUND",
                "CANCEL_REFUND",
                "PRESENT",
                "CLEAR",
                "DISHONOUR",
                "RETURN",
                "CANCEL",
                "CORRECT",
                "CORRECT_DRAWER",
                "ALLOCATE"
              })
          String action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(nullable = true) String reason,
      @Schema(nullable = true) String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {}

  @Schema(
      name = "FinanceCollectionObligationDto",
      requiredProperties = {
        "id",
        "version",
        "logicalId",
        "orderId",
        "partnerId",
        "planId",
        "revisionId",
        "currency",
        "expectedAmount",
        "recordedSettledAmount",
        "recordedOutstandingAmount",
        "receivedChequeAmount",
        "missingInstrumentAmount",
        "overmatchedAmount",
        "refundPendingAmount",
        "kind",
        "method",
        "awaitsConfirmationDate",
        "cancelled",
        "status",
        "sourceMissing",
        "sourceStale",
        "warnings",
        "capabilities"
      })
  public record Obligation(
      UUID id,
      long version,
      UUID logicalId,
      UUID orderId,
      @Schema(
              nullable = true,
              example = "SO-2026-0142",
              description =
                  "The sales order's number, so instalments of repeat orders with the same date"
                      + " and amount can be told apart; null when the order cannot be read")
          String orderNumber,
      UUID partnerId,
      UUID planId,
      UUID revisionId,
      String currency,
      @Schema(type = "string", example = "4284.00") String expectedAmount,
      @Schema(type = "string", example = "1000.00") String recordedSettledAmount,
      @Schema(type = "string", example = "3284.00") String recordedOutstandingAmount,
      @Schema(type = "string") String receivedChequeAmount,
      @Schema(type = "string") String missingInstrumentAmount,
      @Schema(type = "string") String overmatchedAmount,
      @Schema(
              type = "string",
              description = "Cash owed back to the customer by refunds that are still open")
          String refundPendingAmount,
      Kind kind,
      Method method,
      @Schema(nullable = true) LocalDate expectedDate,
      @Schema(nullable = true) LocalDate plannedChequePaymentDate,
      boolean awaitsConfirmationDate,
      boolean cancelled,
      @Schema(
              allowableValues = {
                "OPEN",
                "PARTIALLY_SETTLED",
                "SETTLED",
                "CANCELLED",
                "REVIEW_REQUIRED"
              })
          String status,
      @Schema(nullable = true) Instant sourceCheckedAt,
      boolean sourceMissing,
      boolean sourceStale,
      List<String> warnings,
      List<Capability> capabilities) {}

  @Schema(
      name = "FinanceCollectionPaymentMatchDto",
      requiredProperties = {
        "id",
        "version",
        "obligationId",
        "paymentId",
        "amount",
        "effectiveDate",
        "occurredAt",
        "recordedAt",
        "recordedBy",
        "reason"
      })
  public record Match(
      UUID id,
      long version,
      UUID obligationId,
      UUID paymentId,
      @Schema(nullable = true) UUID chequeId,
      @Schema(type = "string") String amount,
      LocalDate effectiveDate,
      Instant occurredAt,
      Instant recordedAt,
      UUID recordedBy,
      String reason,
      @Schema(nullable = true) LocalDate reversedOn,
      @Schema(nullable = true) Instant reversalOccurredAt,
      @Schema(nullable = true) Instant reversedAt,
      @Schema(nullable = true) UUID reversedBy,
      @Schema(nullable = true) String reversalReason,
      @Schema(nullable = true) Instant paymentInvalidatedAt,
      @Schema(nullable = true) Instant invalidationRecordedAt,
      @Schema(nullable = true) String currentPaymentStatus) {}

  @Schema(
      name = "FinanceCollectionDetailDto",
      requiredProperties = {"obligation", "matches"})
  public record Detail(Obligation obligation, Page<Match> matches) {}

  @Schema(
      name = "FinanceCollectionRefundDto",
      requiredProperties = {
        "id",
        "obligationId",
        "paymentId",
        "partnerId",
        "currency",
        "amount",
        "state",
        "step",
        "effectiveDate",
        "occurredAt",
        "recordedAt",
        "recordedBy",
        "reason",
        "releasedAllocations",
        "capabilities"
      })
  public record Refund(
      UUID id,
      UUID obligationId,
      @Schema(description = "The inbound Payment whose money is owed back") UUID paymentId,
      UUID partnerId,
      String currency,
      @Schema(type = "string", example = "200.00") String amount,
      RefundState state,
      @Schema(description = "Number of lifecycle events; the value a refund command must echo")
          int step,
      @Schema(nullable = true, description = "The outbound Payment that paid it, while settled")
          UUID settlementPaymentId,
      @Schema(description = "When the refund became owed") LocalDate effectiveDate,
      Instant occurredAt,
      Instant recordedAt,
      UUID recordedBy,
      String reason,
      @Schema(description = "Invoice allocations released in the same command to open this refund")
          List<ReleasedAllocation> releasedAllocations,
      List<Capability> capabilities) {}

  @Schema(
      name = "FinanceCollectionReleasedAllocationDto",
      requiredProperties = {"allocationId", "invoiceId", "amount"})
  public record ReleasedAllocation(
      UUID allocationId,
      UUID invoiceId,
      @Schema(type = "string", example = "200.00") String amount) {}

  @Schema(
      name = "FinanceCollectionChequeDto",
      requiredProperties = {
        "id",
        "version",
        "partnerId",
        "currency",
        "amount",
        "unappliedAmount",
        "receivedOn",
        "dueOn",
        "instrumentReference",
        "state",
        "clearingValid",
        "possibleDuplicateReference",
        "applications",
        "capabilities",
        "warnings"
      })
  public record Cheque(
      UUID id,
      long version,
      UUID partnerId,
      String currency,
      @Schema(type = "string") String amount,
      @Schema(type = "string") String unappliedAmount,
      LocalDate receivedOn,
      LocalDate dueOn,
      String instrumentReference,
      ChequeState state,
      @Schema(nullable = true) UUID clearedPaymentId,
      boolean clearingValid,
      boolean possibleDuplicateReference,
      @Schema(nullable = true, description = "Null on cheques received before tagging")
          DrawerKind drawerKind,
      @Schema(nullable = true) UUID drawerId,
      @Schema(nullable = true, description = "The name recorded with the cheque") String drawerName,
      List<Application> applications,
      List<Capability> capabilities,
      List<String> warnings) {}

  @Schema(
      name = "FinanceCollectionChequeApplicationDto",
      description =
          "An active (unreversed) application. Several may name the same obligation; send one"
              + " expected version per distinct obligation.",
      requiredProperties = {
        "id",
        "obligationId",
        "amount",
        "effectiveDate",
        "recordedAt",
        "reversible"
      })
  public record Application(
      UUID id,
      UUID obligationId,
      @Schema(type = "string") String amount,
      LocalDate effectiveDate,
      Instant recordedAt,
      @Schema(description = "Whether this viewer may reverse it now (PAYMENT-PLAN-9 R3a)")
          boolean reversible) {}

  @Schema(
      name = "FinanceCollectionCustomerAvailabilityDto",
      description =
          "PAYMENT-PLAN-9 R5: what a customer has given that is not yet matched or applied to"
              + " payment-plan instalments, per currency. Payments and held cheques are separate"
              + " lines and are never added together; this is not an invoice-allocation balance.",
      requiredProperties = {
        "partnerId",
        "currencies",
        "chequeHistory",
        "paymentsTruncated",
        "chequeHistoryTruncated"
      })
  public record CustomerAvailability(
      UUID partnerId,
      List<CurrencyAvailability> currencies,
      @Schema(
              description =
                  "Cheques that offer nothing for allocation (fully applied, cleared, returned,"
                      + " cancelled or dishonoured), newest first")
          List<HeldCheque> chequeHistory,
      boolean paymentsTruncated,
      boolean chequeHistoryTruncated) {}

  @Schema(
      name = "FinanceCollectionCurrencyAvailabilityDto",
      requiredProperties = {
        "currency",
        "paymentsAvailable",
        "chequesAvailable",
        "payments",
        "cheques"
      })
  public record CurrencyAvailability(
      String currency,
      @Schema(type = "string", description = "Payments available for instalment matching")
          String paymentsAvailable,
      @Schema(type = "string", description = "Held cheques available for application")
          String chequesAvailable,
      List<AvailablePayment> payments,
      List<HeldCheque> cheques) {}

  @Schema(
      name = "FinanceCollectionAvailablePaymentDto",
      requiredProperties = {
        "id",
        "paymentNumber",
        "paymentDate",
        "method",
        "amount",
        "matchedAmount",
        "refundHeldAmount",
        "availableAmount",
        "invoiceAllocatedAmount"
      })
  public record AvailablePayment(
      UUID id,
      String paymentNumber,
      LocalDate paymentDate,
      String method,
      @Schema(type = "string") String amount,
      @Schema(type = "string") String matchedAmount,
      @Schema(type = "string") String refundHeldAmount,
      @Schema(type = "string") String availableAmount,
      @Schema(
              type = "string",
              description =
                  "Invoice allocations of the same payment; information only, not subtracted")
          String invoiceAllocatedAmount,
      @Schema(nullable = true, description = "The cleared cheque this payment is the clearing of")
          UUID clearedChequeId) {}

  @Schema(
      name = "FinanceCollectionHeldChequeDto",
      requiredProperties = {
        "id",
        "version",
        "currency",
        "state",
        "instrumentReference",
        "receivedOn",
        "dueOn",
        "amount",
        "appliedAmount",
        "availableAmount"
      })
  public record HeldCheque(
      UUID id,
      @Schema(description = "The cheque's version, expected by an allocation") long version,
      String currency,
      ChequeState state,
      @Schema(nullable = true) DrawerKind drawerKind,
      @Schema(nullable = true) String drawerName,
      String instrumentReference,
      LocalDate receivedOn,
      LocalDate dueOn,
      @Schema(type = "string") String amount,
      @Schema(type = "string") String appliedAmount,
      @Schema(type = "string") String availableAmount) {}

  @Schema(
      name = "FinanceCollectionChequeEventApplicationDto",
      description =
          "The application an APPLY or REVERSE_APPLICATION event is about. The obligation is"
              + " shown only when the viewer may read it.",
      requiredProperties = {"id", "amount", "targetVisible"})
  public record ChequeEventApplication(
      UUID id,
      @Schema(nullable = true) UUID obligationId,
      @Schema(type = "string") String amount,
      boolean targetVisible) {}

  @Schema(
      name = "FinanceCollectionCommandResult",
      requiredProperties = {
        "operationId",
        "resourceType",
        "resourceId",
        "resourceVersion",
        "matchIds"
      })
  public record Result(
      UUID operationId,
      @Schema(allowableValues = {"OBLIGATION", "CHEQUE", "PAYMENT"}) String resourceType,
      UUID resourceId,
      long resourceVersion,
      @Schema(nullable = true) UUID paymentId,
      List<UUID> matchIds) {}

  @Schema(
      name = "FinanceCollectionExpectationDto",
      requiredProperties = {
        "revisionId",
        "approvalId",
        "agreedAt",
        "activatedAt",
        "recordedAt",
        "amount",
        "kind",
        "method",
        "awaitsConfirmationDate"
      })
  public record Expectation(
      UUID revisionId,
      UUID approvalId,
      Instant agreedAt,
      Instant activatedAt,
      Instant recordedAt,
      @Schema(type = "string") String amount,
      Kind kind,
      Method method,
      @Schema(nullable = true) LocalDate expectedDate,
      boolean awaitsConfirmationDate,
      @Schema(nullable = true) LocalDate chequePaymentDate) {}

  @Schema(
      name = "FinanceCollectionChequeEventDto",
      requiredProperties = {
        "id",
        "action",
        "state",
        "effectiveDate",
        "occurredAt",
        "recordedAt",
        "actorId",
        "reason",
        "evidence",
        "amount",
        "receivedOn",
        "dueOn",
        "instrumentReference"
      })
  public record ChequeEvent(
      UUID id,
      String action,
      ChequeState state,
      LocalDate effectiveDate,
      Instant occurredAt,
      Instant recordedAt,
      UUID actorId,
      String reason,
      String evidence,
      @Schema(type = "string") String amount,
      LocalDate receivedOn,
      LocalDate dueOn,
      String instrumentReference,
      @Schema(nullable = true) UUID paymentId,
      @Schema(nullable = true) DrawerKind drawerKind,
      @Schema(nullable = true) UUID drawerId,
      @Schema(nullable = true) String drawerName,
      @Schema(nullable = true) ChequeEventApplication application) {}
}
