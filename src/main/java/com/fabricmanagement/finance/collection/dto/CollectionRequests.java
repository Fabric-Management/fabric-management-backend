package com.fabricmanagement.finance.collection.dto;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.OverSettlementDecision;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CollectionRequests {
  private CollectionRequests() {}

  @Schema(name = "FinanceCollectionMatchRequest")
  public record Match(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID paymentId,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveDate,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason) {}

  @Schema(name = "FinanceCollectionReceiptRequest")
  public record Receipt(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Method method,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveDate,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @Size(max = 100) @Schema(maxLength = 100, nullable = true) String bankReference,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason) {}

  @Schema(name = "FinanceCollectionReverseMatchRequest")
  public record Reverse(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveDate,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason) {}

  @Schema(
      name = "FinanceCollectionResolveOverSettlementRequest",
      description =
          "Decides what cash above the agreed amount of an expectation is: moved to another"
              + " instalment, kept on account as an advance, or owed back as a refund")
  public record ResolveOverSettlement(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OverSettlementDecision decision,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(
              type = "string",
              example = "200.00",
              description = "The part of the surplus this decision covers; may be partial",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveDate,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 900)
          @Schema(maxLength = 900, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason,
      @Schema(nullable = true, description = "Required for REAPPLY, absent otherwise")
          UUID targetObligationId,
      @PositiveOrZero
          @Schema(nullable = true, description = "Required for REAPPLY, absent otherwise")
          Long targetExpectedVersion,
      @Schema(
              nullable = true,
              description =
                  "REFUND only. When the payment's money is allocated to invoices, release the"
                      + " newest allocations until the refund fits; otherwise the refund is"
                      + " refused. Never done silently.")
          Boolean releaseInvoiceAllocations) {}

  @Schema(name = "FinanceCollectionSettleRefundRequest")
  public record SettleRefund(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedStep,
      @NotNull
          @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "The outbound Payment that gives the money back")
          UUID paymentId,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason) {}

  @Schema(name = "FinanceCollectionCancelRefundRequest")
  public record CancelRefund(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedStep,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveDate,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason) {}

  @Schema(name = "FinanceCollectionChequeApplicationInput")
  public record Application(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID obligationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "4284.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount) {}

  @Schema(name = "FinanceCollectionReceiveChequeRequest")
  public record ReceiveCheque(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partnerId,
      @NotBlank
          @Pattern(regexp = "[A-Z]{3}")
          @Schema(
              pattern = "[A-Z]{3}",
              example = "GBP",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String currency,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "4284.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate receivedOn,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueOn,
      @NotBlank
          @Size(max = 100)
          @Schema(
              maxLength = 100,
              example = "NORTHBANK-0042",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String instrumentReference,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String evidence,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason,
      @NotEmpty
          @Size(max = 500)
          @Valid
          @io.swagger.v3.oas.annotations.media.ArraySchema(
              minItems = 1,
              maxItems = 500,
              schema = @Schema(implementation = Application.class))
          List<Application> applications) {}

  @Schema(name = "FinanceCollectionObligationVersion")
  public record Version(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID obligationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion) {}

  @Schema(name = "FinanceCollectionChequeCorrection")
  public record Correction(
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "4284.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate receivedOn,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueOn,
      @NotBlank
          @Size(max = 100)
          @Schema(maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
          String instrumentReference) {}

  @Schema(name = "FinanceCollectionChequeTransitionRequest")
  public record Transition(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long expectedVersion,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChequeAction action,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveDate,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String evidence,
      @Schema(
              nullable = true,
              description =
                  "Existing inbound payment for CLEAR; absent creates exactly one Payment")
          UUID paymentId,
      @Valid @Schema(nullable = true) Correction correction,
      @Size(max = 500)
          @Valid
          @Schema(
              nullable = true,
              description =
                  "Required for clearing and reversing a cleared cheque; all affected obligation"
                      + " versions")
          List<Version> obligations) {}
}
